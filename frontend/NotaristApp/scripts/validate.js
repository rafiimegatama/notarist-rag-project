#!/usr/bin/env node
/**
 * Frontend validation gate (Sprint 4, Task 12).
 *
 *   node scripts/validate.js
 *
 * Five checks over src/, all static — nothing is executed, no device or Metro needed:
 *
 *   1. parser        every .js file parses as the app's real dialect (JSX + modern syntax)
 *   2. imports       every relative import resolves to a file that exists
 *   3. duplicates    no two components share a name; no unreferenced component files
 *   4. cycles        no circular dependency between modules
 *   5. navigation    every navigate('X') target is a route the navigator registers
 *
 * Uses @babel/parser — already present via babel-preset-expo, and the same parser Metro uses, so
 * "it parses here" means "it parses in the bundler". No new dependency.
 *
 * Written in ES5-compatible CommonJS on purpose: this must run under whatever node is on the box
 * (this one has node 12), not only under the node 24 the app targets. A validation gate that cannot
 * run is not a gate.
 *
 * Exit code 0 = clean, 1 = findings. Suitable for a pre-commit hook or CI step.
 */

'use strict';

var fs = require('fs');
var path = require('path');

var APP_ROOT = path.resolve(__dirname, '..');
var SRC = path.join(APP_ROOT, 'src');

var parser;
try {
  parser = require(path.join(APP_ROOT, 'node_modules/@babel/parser'));
} catch (e) {
  console.error('Cannot load @babel/parser from node_modules. Run `npm install` first.');
  process.exit(2);
}

// The dialect the app is actually written in. Kept in sync with babel-preset-expo's surface: JSX,
// plus the proposals already used across src/ (optional chaining, nullish, class properties).
var PARSER_OPTIONS = {
  sourceType: 'module',
  plugins: ['jsx', 'classProperties', 'objectRestSpread', 'optionalChaining', 'nullishCoalescingOperator', 'dynamicImport'],
  errorRecovery: false,
};

// ---------------------------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------------------------

function walk(dir, out) {
  out = out || [];
  var entries = fs.readdirSync(dir, { withFileTypes: true });
  for (var i = 0; i < entries.length; i++) {
    var e = entries[i];
    var full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === 'node_modules' || e.name.charAt(0) === '.') continue;
      walk(full, out);
    } else if (/\.jsx?$/.test(e.name)) {
      out.push(full);
    }
  }
  return out;
}

function rel(file) {
  return path.relative(APP_ROOT, file);
}

var findings = [];
function report(check, file, message) {
  findings.push({ check: check, file: file ? rel(file) : '-', message: message });
}

// Resolve a relative import the way Metro does: exact, then extensions, then /index.
function resolveImport(fromFile, spec) {
  var base = path.resolve(path.dirname(fromFile), spec);
  var candidates = [
    base,
    base + '.js', base + '.jsx', base + '.json',
    // Platform-specific resolution — Metro prefers these and they must count as "exists".
    base + '.native.js', base + '.ios.js', base + '.android.js', base + '.web.js',
    path.join(base, 'index.js'), path.join(base, 'index.jsx'),
  ];
  for (var i = 0; i < candidates.length; i++) {
    try {
      var st = fs.statSync(candidates[i]);
      if (st.isFile()) return candidates[i];
    } catch (_) { /* next */ }
  }
  return null;
}

// ---------------------------------------------------------------------------------------------
// 1. parser validation  +  AST collection
// ---------------------------------------------------------------------------------------------

var files = walk(SRC);
// App.js / index.js live at the root and are part of the graph.
['App.js', 'index.js'].forEach(function (f) {
  var p = path.join(APP_ROOT, f);
  if (fs.existsSync(p)) files.push(p);
});

var asts = {};       // file -> ast
var sources = {};    // file -> source

files.forEach(function (file) {
  var src = fs.readFileSync(file, 'utf8');
  sources[file] = src;
  try {
    asts[file] = parser.parse(src, PARSER_OPTIONS);
  } catch (err) {
    var loc = err.loc ? ':' + err.loc.line + ':' + err.loc.column : '';
    report('parser', file, 'syntax error' + loc + ' — ' + err.message.split('\n')[0]);
  }
});

// Minimal AST walker: recurses plain objects/arrays looking for node `type`s.
function visit(node, fn) {
  if (!node || typeof node !== 'object') return;
  if (Array.isArray(node)) {
    for (var i = 0; i < node.length; i++) visit(node[i], fn);
    return;
  }
  if (typeof node.type === 'string') fn(node);
  for (var key in node) {
    if (!Object.prototype.hasOwnProperty.call(node, key)) continue;
    if (key === 'loc' || key === 'leadingComments' || key === 'trailingComments') continue;
    var child = node[key];
    if (child && typeof child === 'object') visit(child, fn);
  }
}

// ---------------------------------------------------------------------------------------------
// 2. import validation  +  dependency graph
// ---------------------------------------------------------------------------------------------

var graph = {};      // file -> [resolved deps]

Object.keys(asts).forEach(function (file) {
  var deps = [];
  visit(asts[file], function (node) {
    var spec = null;
    if (node.type === 'ImportDeclaration') spec = node.source.value;
    else if (node.type === 'ExportNamedDeclaration' && node.source) spec = node.source.value;
    else if (node.type === 'ExportAllDeclaration' && node.source) spec = node.source.value;
    else if (node.type === 'CallExpression' && node.callee && node.callee.type === 'Import'
             && node.arguments[0] && node.arguments[0].type === 'StringLiteral') {
      spec = node.arguments[0].value;
    }
    if (!spec) return;
    // Only relative specifiers are ours to verify; bare ones are npm packages.
    if (spec.charAt(0) !== '.') return;
    var resolved = resolveImport(file, spec);
    if (!resolved) report('imports', file, "unresolved import '" + spec + "'");
    else deps.push(resolved);
  });
  graph[file] = deps;
});

// ---------------------------------------------------------------------------------------------
// 3. duplicate component detection
// ---------------------------------------------------------------------------------------------

var COMPONENTS_DIR = path.join(SRC, 'components');
var componentFiles = fs.existsSync(COMPONENTS_DIR)
  ? fs.readdirSync(COMPONENTS_DIR).filter(function (f) { return /\.js$/.test(f) && f !== 'index.js'; })
  : [];

// Same component name exported from two files — the "two EmptyStates" failure mode Task 6 guards.
var byName = {};
componentFiles.forEach(function (f) {
  var name = f.replace(/\.js$/, '');
  (byName[name] = byName[name] || []).push(f);
});
Object.keys(byName).forEach(function (name) {
  if (byName[name].length > 1) {
    report('duplicates', null, "component '" + name + "' defined in: " + byName[name].join(', '));
  }
});

// A component file nothing references is either dead or a duplicate someone forgot to delete.
//
// The barrel (components/index.js) re-exports every component, so a naive "who imports this FILE"
// sweep reports the whole components/ directory as dead the moment one screen imports through the
// barrel — which is exactly what the first version of this check did. References must be traced
// THROUGH the barrel: resolve each of its re-exports to a file, then treat `import { X } from
// '../components'` as a reference to the file that actually defines X.
var BARREL = path.join(COMPONENTS_DIR, 'index.js');

// exported name -> defining file, e.g. 'EmptyState' -> src/components/EmptyState.js
var barrelExports = {};
if (asts[BARREL]) {
  visit(asts[BARREL], function (node) {
    if (node.type !== 'ExportNamedDeclaration' || !node.source) return;
    var target = resolveImport(BARREL, node.source.value);
    if (!target) return;
    node.specifiers.forEach(function (spec) {
      if (spec.type === 'ExportSpecifier') {
        var name = spec.exported.name || spec.exported.value;
        barrelExports[name] = target;
      }
    });
  });
}

var referenced = {};
Object.keys(asts).forEach(function (file) {
  if (file === BARREL) return; // the barrel re-exporting a file is not a use of it
  visit(asts[file], function (node) {
    if (node.type !== 'ImportDeclaration') return;
    var spec = node.source.value;
    if (spec.charAt(0) !== '.') return;
    var target = resolveImport(file, spec);
    if (!target) return;

    if (target === BARREL) {
      // Imported through the barrel: credit the file behind each named specifier.
      node.specifiers.forEach(function (s) {
        if (s.type === 'ImportSpecifier') {
          var name = s.imported.name || s.imported.value;
          if (barrelExports[name]) referenced[barrelExports[name]] = true;
        }
      });
    } else {
      referenced[target] = true;
    }
  });
});

componentFiles.forEach(function (f) {
  var full = path.join(COMPONENTS_DIR, f);
  if (!referenced[full]) {
    report('duplicates', full, 'component is never imported (dead code, or a duplicate left behind?)');
  }
});

// ---------------------------------------------------------------------------------------------
// 4. circular dependency detection  (iterative DFS — no recursion limit surprises)
// ---------------------------------------------------------------------------------------------

var WHITE = 0, GREY = 1, BLACK = 2;
var color = {};
var reportedCycles = {};

Object.keys(graph).forEach(function (root) {
  if (color[root]) return;
  var stack = [{ file: root, i: 0 }];
  color[root] = GREY;

  while (stack.length) {
    var top = stack[stack.length - 1];
    var deps = graph[top.file] || [];
    if (top.i >= deps.length) {
      color[top.file] = BLACK;
      stack.pop();
      continue;
    }
    var dep = deps[top.i++];
    if (color[dep] === GREY) {
      // Found a back edge: slice the cycle out of the current stack.
      var names = stack.map(function (s) { return s.file; });
      var start = names.indexOf(dep);
      var cycle = names.slice(start).concat([dep]).map(rel);
      // Normalize so the same cycle found from two entry points is reported once.
      var key = cycle.slice(0, -1).slice().sort().join('|');
      if (!reportedCycles[key]) {
        reportedCycles[key] = true;
        report('cycles', null, 'circular dependency: ' + cycle.join(' → '));
      }
    } else if (color[dep] !== BLACK && graph[dep]) {
      color[dep] = GREY;
      stack.push({ file: dep, i: 0 });
    }
  }
});

// ---------------------------------------------------------------------------------------------
// 5. navigation validation
// ---------------------------------------------------------------------------------------------

var NAV_FILE = path.join(SRC, 'navigation/AppNavigator.js');
if (asts[NAV_FILE]) {
  var routes = {};
  // <Stack.Screen name="X" /> and <Tab.Screen name="X" />
  visit(asts[NAV_FILE], function (node) {
    if (node.type !== 'JSXOpeningElement') return;
    var el = node.name;
    var isScreen = el && el.type === 'JSXMemberExpression'
      && el.property && el.property.name === 'Screen';
    if (!isScreen) return;
    node.attributes.forEach(function (attr) {
      if (attr.type === 'JSXAttribute' && attr.name.name === 'name'
          && attr.value && attr.value.type === 'StringLiteral') {
        routes[attr.value.value] = true;
      }
    });
  });

  // Nested navigators are reached as navigate('Main', { screen: 'Asisten' }); the inner name is a
  // route of the tab navigator, which the sweep above already collected.
  Object.keys(asts).forEach(function (file) {
    visit(asts[file], function (node) {
      if (node.type !== 'CallExpression') return;
      var callee = node.callee;
      if (!callee || callee.type !== 'MemberExpression') return;
      if (!callee.property || callee.property.name !== 'navigate') return;
      var arg = node.arguments[0];
      if (!arg || arg.type !== 'StringLiteral') return; // dynamic target — not statically checkable
      if (!routes[arg.value]) {
        report('navigation', file, "navigate('" + arg.value + "') — no screen with that name is registered");
      }
      // Check the nested { screen: 'X' } form too.
      var second = node.arguments[1];
      if (second && second.type === 'ObjectExpression') {
        second.properties.forEach(function (p) {
          if (p.type === 'ObjectProperty' && p.key && p.key.name === 'screen'
              && p.value && p.value.type === 'StringLiteral' && !routes[p.value.value]) {
            report('navigation', file, "navigate(… { screen: '" + p.value.value + "' }) — not a registered screen");
          }
        });
      }
    });
  });
} else {
  report('navigation', NAV_FILE, 'navigator not found — navigation validation skipped');
}

// ---------------------------------------------------------------------------------------------
// output
// ---------------------------------------------------------------------------------------------

var ORDER = ['parser', 'imports', 'duplicates', 'cycles', 'navigation'];
var LABEL = {
  parser: 'Parser validation',
  imports: 'Import validation',
  duplicates: 'Duplicate component detection',
  cycles: 'Circular dependency detection',
  navigation: 'Navigation validation',
};

console.log('\nFrontend validation — ' + files.length + ' files\n');

var failed = 0;
ORDER.forEach(function (check) {
  var hits = findings.filter(function (f) { return f.check === check; });
  if (!hits.length) {
    console.log('  PASS  ' + LABEL[check]);
    return;
  }
  failed += hits.length;
  console.log('  FAIL  ' + LABEL[check] + '  (' + hits.length + ')');
  hits.forEach(function (h) {
    console.log('        · ' + (h.file !== '-' ? h.file + ': ' : '') + h.message);
  });
});

console.log('');
if (failed) {
  console.log(failed + ' finding(s).\n');
  process.exit(1);
}
console.log('All checks passed.\n');
process.exit(0);
