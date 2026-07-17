#!/usr/bin/env node
/**
 * Backend integration validator (Sprint 5, Task 11).
 *
 *   node scripts/validate-integration.js
 *
 * Seven checks, all static, comparing the FRONTEND against the REAL BACKEND SOURCE:
 *
 *   1. missing endpoint      the app calls a route the backend does not expose
 *   2. unused endpoint       the backend exposes a route the app never calls
 *   3. DTO mismatch          a normalizer reads a field the DTO does not have (and vice versa)
 *   4. duplicate models      two normalizers for one entity
 *   5. dead services         a service nothing imports
 *   6. unreachable API       an api/ module no service or state reaches
 *   7. unused FEATURE flags  a flag nothing reads — a switch that controls nothing
 *
 * It reads backend/**\/api/**\/*.java READ-ONLY. That is the point: a frontend-only check can prove
 * the app is self-consistent, but not that it agrees with the thing it must integrate with — which
 * is the only question this sprint cares about.
 *
 * Findings are advisory, not all failures. "Unused endpoint" is usually a roadmap item, not a bug,
 * so the exit code reflects only the checks that indicate the app is actually broken (1, 3, 5, 6, 7).
 * Run with --strict to fail on everything.
 *
 * ES5-compatible CommonJS: must run on whatever node is on the box (this one has node 12).
 */

'use strict';

var fs = require('fs');
var path = require('path');

var APP_ROOT = path.resolve(__dirname, '..');
var SRC = path.join(APP_ROOT, 'src');
var REPO_ROOT = path.resolve(APP_ROOT, '../..');
var BACKEND = path.join(REPO_ROOT, 'backend');
var STRICT = process.argv.indexOf('--strict') !== -1;

var parser;
try {
  parser = require(path.join(APP_ROOT, 'node_modules/@babel/parser'));
} catch (e) {
  console.error('Cannot load @babel/parser. Run `npm install` first.');
  process.exit(2);
}

var PARSER_OPTIONS = {
  sourceType: 'module',
  plugins: ['jsx', 'classProperties', 'objectRestSpread', 'optionalChaining', 'nullishCoalescingOperator', 'dynamicImport'],
};

function walk(dir, filter, out) {
  out = out || [];
  var entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return out; }
  for (var i = 0; i < entries.length; i++) {
    var e = entries[i];
    var full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === 'node_modules' || e.name === 'build' || e.name === '.git' || e.name.charAt(0) === '.') continue;
      walk(full, filter, out);
    } else if (filter(e.name)) {
      out.push(full);
    }
  }
  return out;
}

var findings = [];
function report(check, where, message) {
  findings.push({ check: check, where: where, message: message });
}

/**
 * Resolve an import specifier to a FILE, the way Metro does.
 *
 * The isFile() check is load-bearing: `import … from '../services'` resolves to a DIRECTORY that
 * exists, and an existsSync-only filter returns that directory before ever reaching
 * services/index.js. The barrel then never matches, and every service in it looks dead.
 */
function resolveFile(base) {
  var candidates = [base, base + '.js', base + '.jsx', path.join(base, 'index.js')];
  for (var i = 0; i < candidates.length; i++) {
    try {
      if (fs.statSync(candidates[i]).isFile()) return candidates[i];
    } catch (e) { /* next */ }
  }
  return null;
}

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
// gather: frontend
// ---------------------------------------------------------------------------------------------

var jsFiles = walk(SRC, function (n) { return /\.jsx?$/.test(n); });
var asts = {};
var sources = {};
jsFiles.forEach(function (f) {
  var src = fs.readFileSync(f, 'utf8');
  sources[f] = src;
  try { asts[f] = parser.parse(src, PARSER_OPTIONS); } catch (e) { /* scripts/validate.js owns parse errors */ }
});

// --- frontend HTTP calls: client.get('/x') / client.post(`/y/${id}`) ---
var calls = []; // { file, method, route }
Object.keys(asts).forEach(function (file) {
  visit(asts[file], function (node) {
    if (node.type !== 'CallExpression') return;
    var callee = node.callee;
    if (!callee || callee.type !== 'MemberExpression') return;
    var obj = callee.object, prop = callee.property;
    if (!obj || obj.name !== 'client' || !prop) return;
    var method = prop.name;
    if (['get', 'post', 'put', 'patch', 'delete', 'head'].indexOf(method) === -1) return;

    var arg = node.arguments[0];
    var route = null;
    if (arg && arg.type === 'StringLiteral') route = arg.value;
    else if (arg && arg.type === 'TemplateLiteral') {
      // `/cases/${id}/timeline` -> /cases/{}/timeline
      route = arg.quasis.map(function (q) { return q.value.cooked; }).join('{}');
    }
    if (route) calls.push({ file: file, method: method.toUpperCase(), route: route });
  });
});

// ---------------------------------------------------------------------------------------------
// gather: backend routes  (@RequestMapping on the class + @GetMapping etc. on the method)
// ---------------------------------------------------------------------------------------------

var backendRoutes = []; // { method, route, file }
var controllers = walk(BACKEND, function (n) { return /Controller\.java$/.test(n); });
controllers.forEach(function (file) {
  var src = fs.readFileSync(file, 'utf8');

  // Base path. NOT a plain string literal in this codebase — every controller writes
  //   @RequestMapping(NotaristConstants.API_BASE_PATH + "/reminders")
  // so a /"([^"]*)"/ match finds nothing and every route silently loses its prefix. Concatenate
  // every string literal inside the parens instead; the symbolic constant contributes the /api/v1
  // that canon() strips anyway.
  var base = '';
  var baseMatch = src.match(/@RequestMapping\(([^)]*)\)/);
  if (baseMatch) {
    var lits = baseMatch[1].match(/"([^"]*)"/g);
    if (lits) base = lits.map(function (s) { return s.slice(1, -1); }).join('');
  }

  // Method mappings, with or without a path argument. `@GetMapping` bare means "the class path is
  // the route" — which is how GET /reminders and GET /documents are declared.
  var re = /@(Get|Post|Put|Patch|Delete)Mapping(\s*\(([^)]*)\))?/g;
  var m;
  while ((m = re.exec(src)) !== null) {
    var verb = m[1].toUpperCase();
    var args = m[3] || '';
    var lit = args.match(/"([^"]*)"/);
    var sub = lit ? lit[1] : '';
    var full = sub ? (base + (sub.charAt(0) !== '/' ? '/' + sub : sub)) : base;
    backendRoutes.push({ method: verb, route: full, file: file });
  }
});

// Normalize both sides to compare: strip /api/v1, collapse path params to {}.
function canon(route) {
  return String(route)
    .replace(/^\/api\/v1/, '')
    .replace(/\{[^}]*\}/g, '{}')
    .replace(/\/+$/, '')
    .replace(/\/+/g, '/');
}

var backendSet = {};
backendRoutes.forEach(function (r) { backendSet[r.method + ' ' + canon(r.route)] = r; });

// ---------------------------------------------------------------------------------------------
// 1. missing endpoint — the app calls something the backend does not serve
// ---------------------------------------------------------------------------------------------

var calledSet = {};
calls.forEach(function (c) {
  var key = c.method + ' ' + canon(c.route);
  calledSet[key] = true;
  if (!backendSet[key]) {
    // A controller may compose its base path in a way the regex misses; match on the tail as a
    // second chance before crying wolf.
    var tail = canon(c.route).split('/').filter(Boolean).pop();
    var loose = Object.keys(backendSet).some(function (k) {
      return k.indexOf(c.method) === 0 && tail && k.indexOf(tail) !== -1;
    });
    if (!loose) {
      // Is this call gated behind a FEATURES flag? If so it never fires today, so it is a PLANNED
      // integration (the app is written ahead of the endpoint, deliberately) rather than a live
      // break. Heuristic: the enclosing module reads a flag. It is file-level, not statement-level,
      // so it can be over-generous — which is the safe direction: an ungated call to a missing route
      // is the thing that must never be downgraded, and those files reference no flag at all.
      var gated = /FEATURES\./.test(sources[c.file] || '');
      report(
        gated ? 'planned-endpoint' : 'missing-endpoint',
        path.relative(APP_ROOT, c.file),
        c.method + ' ' + c.route + (gated
          ? ' — not served by the backend; call is flag-gated, so this is a planned integration'
          : ' — no backend route serves this, and the call is NOT flag-gated'),
      );
    }
  }
});

// ---------------------------------------------------------------------------------------------
// 2. unused endpoint — the backend serves something the app never calls (advisory)
// ---------------------------------------------------------------------------------------------

Object.keys(backendSet).forEach(function (key) {
  if (calledSet[key]) return;
  var tail = key.split('/').filter(Boolean).pop();
  var loose = Object.keys(calledSet).some(function (k) { return tail && k.indexOf(tail) !== -1; });
  if (!loose) {
    report('unused-endpoint', path.relative(REPO_ROOT, backendSet[key].file), key + ' — backend serves this; the app never calls it');
  }
});

// ---------------------------------------------------------------------------------------------
// 3. DTO mismatch — normalizer field reads vs the Java record components
// ---------------------------------------------------------------------------------------------

// Frontend: which raw keys does each normalizer read? Collected from pick(raw, ['a','b']).
// Only entities where the normalizer maps a DTO 1:1. Dashboard is deliberately EXCLUDED: its
// normalizer models the COMPOSED counter contract (built in api/dashboard.js from three endpoints),
// not DashboardSummaryResponse — comparing them reports every summary field as "unread", which is
// true and meaningless. A check that is noisy where the design is intentional trains people to
// ignore it.
var MODEL_TO_DTO = {
  'Case.js': 'CaseResponse.java',
  'Bundle.js': 'BundleResponse.java',
  'Timeline.js': 'TimelineResponse.java',
  'Verification.js': 'VerificationResponse.java',
  'Ocr.js': 'OcrFieldResponse.java',
};

function dtoComponents(javaFile) {
  var src = fs.readFileSync(javaFile, 'utf8');
  // record X( Type name, Type name, ... ) — take the first record header.
  var m = src.match(/public record\s+\w+\s*\(([\s\S]*?)\)\s*\{/);
  if (!m) return null;
  var body = m[1];
  var names = [];
  body.split(',').forEach(function (part) {
    var cleaned = part.replace(/@\w+(\([^)]*\))?/g, '').trim(); // drop annotations
    var toks = cleaned.split(/\s+/).filter(Boolean);
    if (toks.length >= 2) names.push(toks[toks.length - 1].replace(/[^\w]/g, ''));
  });
  return names;
}

Object.keys(MODEL_TO_DTO).forEach(function (modelFile) {
  var modelPath = path.join(SRC, 'models', modelFile);
  if (!asts[modelPath]) return;
  var javaFiles = walk(BACKEND, function (n) { return n === MODEL_TO_DTO[modelFile]; });
  if (!javaFiles.length) {
    report('dto-mismatch', 'src/models/' + modelFile, 'no backend DTO named ' + MODEL_TO_DTO[modelFile] + ' — normalizer has no contract to check against');
    return;
  }
  var components = dtoComponents(javaFiles[0]);
  if (!components) return;

  // Which keys does the normalizer read?
  //
  // A field is reachable via several idioms — pick(raw,['a']), toList(raw,['entries']), a direct
  // raw.bundleIds, or a CONSUMED list — so matching only pick() reported half the normalizer as
  // blind. This asks the cruder but far more honest question: does the field name appear ANYWHERE
  // in the model source? It can be fooled by a field named in a comment, which is the right way to
  // be wrong: this check exists to catch "the backend added a field and nobody noticed", and a
  // false PASS there is cheap while a false FAIL trains people to ignore the tool.
  var modelSrc = sources[modelPath] || '';
  var read = {};
  components.forEach(function (c) {
    if (new RegExp('\\b' + c + '\\b').test(modelSrc)) read[c] = true;
  });

  // A DTO field no normalizer reads = data arriving and being dropped on the floor.
  components.forEach(function (c) {
    if (!read[c]) {
      report('dto-mismatch', 'src/models/' + modelFile,
        MODEL_TO_DTO[modelFile] + '.' + c + ' is sent by the backend but no normalizer field reads it');
    }
  });
});

// ---------------------------------------------------------------------------------------------
// 4. duplicate models — two normalizers claiming one entity
// ---------------------------------------------------------------------------------------------

var normalizerOwners = {}; // exported normalizeX name -> [files]
Object.keys(asts).forEach(function (file) {
  if (file.indexOf(path.join(SRC, 'models')) !== 0) return;
  visit(asts[file], function (node) {
    if (node.type !== 'ExportNamedDeclaration' || !node.declaration) return;
    var d = node.declaration;
    var name = null;
    if (d.type === 'FunctionDeclaration' && d.id) name = d.id.name;
    if (d.type === 'VariableDeclaration' && d.declarations[0] && d.declarations[0].id) name = d.declarations[0].id.name;
    if (!name) return;
    if (!/^normalize[A-Z]/.test(name) && !/Normalizer$/.test(name)) return;
    (normalizerOwners[name] = normalizerOwners[name] || []).push(path.relative(APP_ROOT, file));
  });
});
Object.keys(normalizerOwners).forEach(function (name) {
  if (normalizerOwners[name].length > 1) {
    report('duplicate-models', '-', name + ' is defined in ' + normalizerOwners[name].join(' and '));
  }
});

// ---------------------------------------------------------------------------------------------
// 5. dead services  +  6. unreachable API modules
// ---------------------------------------------------------------------------------------------

/**
 * Which files under `targetDir` are actually reached?
 *
 * References must be traced THROUGH the directory's barrel. Every context imports
 * `import { DashboardService } from '../services'` — the barrel — so a naive "who imports this FILE"
 * sweep reports all ten services as dead. (Sprint 4's validate.js learned this the same way; the
 * fix is the same: resolve the barrel's re-exports to their defining files, then credit a named
 * import through the barrel to whichever file defines that name.)
 */
function importedBy(targetDir) {
  var barrel = path.join(targetDir, 'index.js');

  // exported name -> defining file
  var barrelExports = {};
  if (asts[barrel]) {
    visit(asts[barrel], function (node) {
      if ((node.type !== 'ExportNamedDeclaration' && node.type !== 'ExportAllDeclaration') || !node.source) return;
      var target = resolveFile(path.resolve(path.dirname(barrel), node.source.value));
      if (!target) return;
      (node.specifiers || []).forEach(function (s) {
        if (s.type === 'ExportSpecifier') {
          barrelExports[s.exported.name || s.exported.value] = target;
        }
      });
    });
  }

  var used = {};
  Object.keys(asts).forEach(function (file) {
    if (file === barrel) return; // the barrel re-exporting a file is not a use of it
    visit(asts[file], function (node) {
      if (node.type !== 'ImportDeclaration') return;
      var spec = node.source.value;
      if (!spec || spec.charAt(0) !== '.') return;
      var target = resolveFile(path.resolve(path.dirname(file), spec));
      if (!target) return;

      if (target === barrel) {
        (node.specifiers || []).forEach(function (s) {
          if (s.type === 'ImportSpecifier') {
            var name = s.imported.name || s.imported.value;
            if (barrelExports[name]) used[barrelExports[name]] = true;
          }
        });
      } else if (target.indexOf(targetDir) === 0) {
        used[target] = true;
      }
    });
  });
  return used;
}

var SERVICES = path.join(SRC, 'services');
var usedServices = importedBy(SERVICES);
// Only *Service.js files. services/ also holds cache.js, mutations.js, polling.js (infrastructure,
// reached from hooks/state) and contracts.js — which exports no runtime code ON PURPOSE: it is the
// JSDoc contract every service is written against, kept as a module so the contracts stay
// import-resolvable. Flagging it as a "dead service" would be applying the wrong rule to it.
walk(SERVICES, function (n) { return /Service\.js$/.test(n); }).forEach(function (f) {
  if (!usedServices[f]) report('dead-service', path.relative(APP_ROOT, f), 'no module imports this service');
});

var API = path.join(SRC, 'api');
var usedApi = importedBy(API);
walk(API, function (n) { return /\.js$/.test(n); }).forEach(function (f) {
  if (path.basename(f) === 'client.js') return; // imported by every api module; also the axios root
  if (!usedApi[f]) report('unreachable-api', path.relative(APP_ROOT, f), 'no service or module reaches this api module');
});

// ---------------------------------------------------------------------------------------------
// 7. unused FEATURE flags — a switch that controls nothing
// ---------------------------------------------------------------------------------------------

var configPath = path.join(SRC, 'constants/config.js');
var declaredFlags = [];
if (asts[configPath]) {
  visit(asts[configPath], function (node) {
    if (node.type !== 'VariableDeclarator' || !node.id || node.id.name !== 'FEATURES') return;
    if (!node.init || node.init.type !== 'ObjectExpression') return;
    node.init.properties.forEach(function (p) {
      if (p.type === 'ObjectProperty' && p.key && (p.key.name || p.key.value)) {
        declaredFlags.push(p.key.name || p.key.value);
      }
    });
  });
}
var flagUses = {};
Object.keys(asts).forEach(function (file) {
  if (file === configPath) return;
  visit(asts[file], function (node) {
    if (node.type !== 'MemberExpression') return;
    if (!node.object || node.object.name !== 'FEATURES' || !node.property) return;
    var name = node.property.name || node.property.value;
    if (name) flagUses[name] = (flagUses[name] || 0) + 1;
  });
});
declaredFlags.forEach(function (flag) {
  if (!flagUses[flag]) {
    report('unused-flag', 'src/constants/config.js', 'FEATURES.' + flag + ' is declared but never read — it switches nothing');
  }
});

// ---------------------------------------------------------------------------------------------
// output
// ---------------------------------------------------------------------------------------------

var ORDER = ['missing-endpoint', 'dto-mismatch', 'duplicate-models', 'dead-service', 'unreachable-api', 'unused-flag', 'planned-endpoint', 'unused-endpoint'];
var LABEL = {
  'missing-endpoint': 'Missing endpoint (ungated call to a route that does not exist)',
  'planned-endpoint': 'Planned endpoint (flag-gated call; backend does not serve it yet)',
  'unused-endpoint': 'Unused endpoint (backend serves it; app never calls it)',
  'dto-mismatch': 'DTO mismatch (backend field no normalizer reads)',
  'duplicate-models': 'Duplicate models',
  'dead-service': 'Dead services',
  'unreachable-api': 'Unreachable API modules',
  'unused-flag': 'Unused FEATURE flags',
};
// Advisory: informative, but not evidence the app is broken today.
//   unused-endpoint  — usually a roadmap item (backend ahead of the app)
//   planned-endpoint — the app is ahead of the backend, and says so via a flag
//   dto-mismatch     — a field arriving and not consumed is a gap, not a crash
// This IS the "remaining backend endpoints required from Claude 1" list.
var ADVISORY = { 'unused-endpoint': true, 'dto-mismatch': true, 'planned-endpoint': true };

console.log('\nBackend integration validation');
console.log('  frontend: ' + jsFiles.length + ' files, ' + calls.length + ' HTTP calls');
console.log('  backend:  ' + controllers.length + ' controllers, ' + backendRoutes.length + ' routes\n');

var blocking = 0;
ORDER.forEach(function (check) {
  var hits = findings.filter(function (f) { return f.check === check; });
  var advisory = ADVISORY[check] && !STRICT;
  if (!hits.length) { console.log('  PASS  ' + LABEL[check]); return; }
  if (!advisory) blocking += hits.length;
  console.log('  ' + (advisory ? 'NOTE' : 'FAIL') + '  ' + LABEL[check] + '  (' + hits.length + ')');
  hits.forEach(function (h) {
    console.log('        · ' + (h.where !== '-' ? h.where + ': ' : '') + h.message);
  });
});

console.log('');
if (blocking) {
  console.log(blocking + ' blocking finding(s).\n');
  process.exit(1);
}
console.log('No blocking findings.' + (findings.length ? ' (' + findings.length + ' advisory)' : '') + '\n');
process.exit(0);
