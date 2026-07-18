#!/usr/bin/env node
/**
 * Backend integration validator (Sprint 5, Task 11; extended Sprint 6).
 *
 *   node scripts/validate-integration.js [--strict]
 *
 * Static checks comparing the FRONTEND against the REAL BACKEND SOURCE:
 *
 *    1. missing endpoint       the app calls a route the backend does not expose
 *    1b. wrong HTTP method     the path exists, the verb does not (a 405)
 *    2. unused endpoint        the backend exposes a route the app never calls
 *    3. DTO mismatch           a DTO field no normalizer reads
 *    3b. impossible field      a normalizer read no DTO can ever satisfy
 *    3c. enum agreement        a literal the app sends that the Java enum does not contain,
 *                              and a member the Java enum sends that the app cannot handle
 *    4. duplicate models       two normalizers for one entity
 *    5. dead services          a service nothing imports
 *    6. unreachable API        an api/ module no service or state reaches
 *    7. unused FEATURE flags   a flag nothing reads — a switch that controls nothing
 *    8. stale flag comment     a comment claiming a route is absent that the backend serves
 *    9. raw envelope access    hand-rolled `.data.data` instead of unwrap()
 *   10. pagination shape       the page descriptor read from fields PageInfo does not send
 *
 * It reads backend/**\/*.java READ-ONLY. That is the point: a frontend-only check can prove the app
 * is self-consistent, but not that it agrees with the thing it must integrate with — which is the
 * only question this sprint cares about.
 *
 * WHAT THIS TOOL IS FOR, since it decides what belongs in it:
 *
 * Sprint 6 found a shipped P0 (`intentOverride: 'LOOKUP'`, an enum member that does not exist, so
 * every structured search was a 400) that no test and no earlier version of this script could see.
 * The frontend and the backend agreed on nothing but a URL, and the vocabularies only met at runtime.
 * So the rule here is: if a mismatch between these two codebases has a DETERMINISTIC failure, this
 * script must fail on it, statically, before anyone runs the app.
 *
 * The corollary matters just as much. A check that reports a real problem as a PASS is worse than no
 * check — Sprint 6 deleted a "tail heuristic" from checks 1 and 2 that did exactly that, silently
 * swallowing two live-on-flag-flip 404s. Precision over reach: no heuristic that can turn a finding
 * into a pass, and every allowlist entry carries a written reason.
 *
 * Findings are advisory, not all failures. "Unused endpoint" is usually a roadmap item, not a bug, so
 * the exit code reflects only the checks that indicate the app is actually broken (see ADVISORY at
 * the bottom). Run with --strict to fail on everything.
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

// --- frontend HTTP calls: client.get('/x') / client.post(`/y/${id}`) / axios.post(`${BASE_URL}/z`) ---
//
// THREE HTTP callers exist, not one. `client` is the configured axios instance every api module uses;
// bare `axios` appears once, in client.js#performRefresh, which MUST bypass the interceptors it would
// otherwise recurse through. Matching only `client` missed it entirely, so POST /auth/refresh — a
// call this app makes on every token expiry — was reported as an endpoint "the app never calls".
// The third caller is XMLHttpRequest: api/assistantStream.js streams SSE via xhr.open(method, url),
// because nothing axios offers can read a response incrementally on native RN. Not matching it made
// this validator report POST /assistant/ask/stream as "the app never calls it" — the exact false
// negative the axios lesson above was about, one transport over.
var HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'head'];
var HTTP_CALLERS = ['client', 'axios'];

// Identifiers that resolve to the API base at runtime. In a route template these contribute the
// /api/v1 prefix that canon() strips anyway, so they must collapse to '' rather than to a path
// param — `${BASE_URL}/auth/refresh` is /auth/refresh, NOT {}/auth/refresh.
var BASE_URL_IDENTS = ['BASE_URL', 'API_BASE', 'API_BASE_URL', 'baseURL'];

/**
 * The route a call expression's first argument denotes, with path params collapsed to {}.
 * Returns null when the argument is not a literal route this can read statically.
 */
function routeFromArg(arg) {
  if (!arg) return null;
  if (arg.type === 'StringLiteral') return arg.value;
  if (arg.type !== 'TemplateLiteral') return null;

  // Walk quasis and expressions together. Every `${…}` becomes '{}' (a path param) EXCEPT a bare
  // base-url identifier, which becomes ''. Joining the quasis with a blanket '{}' — as this did
  // before — cannot express that difference, and got /auth/refresh wrong because of it.
  var out = '';
  for (var i = 0; i < arg.quasis.length; i++) {
    out += arg.quasis[i].value.cooked;
    if (i < arg.expressions.length) {
      var ex = arg.expressions[i];
      var isBase = ex && ex.type === 'Identifier' && BASE_URL_IDENTS.indexOf(ex.name) !== -1;
      out += isBase ? '' : '{}';
    }
  }
  return out;
}

var calls = []; // { file, method, route, node }
Object.keys(asts).forEach(function (file) {
  visit(asts[file], function (node) {
    if (node.type !== 'CallExpression') return;
    var callee = node.callee;
    if (!callee || callee.type !== 'MemberExpression') return;
    var obj = callee.object, prop = callee.property;
    if (!obj || !prop) return;

    // XMLHttpRequest: <anything>.open('POST', route). The method is a string ARGUMENT here, not the
    // property name — the property is always `open` — so this cannot fold into the axios branch.
    if (prop.name === 'open' && node.arguments.length >= 2 &&
        node.arguments[0] && node.arguments[0].type === 'StringLiteral' &&
        HTTP_METHODS.indexOf(node.arguments[0].value.toLowerCase()) !== -1) {
      var xhrRoute = routeFromArg(node.arguments[1]);
      if (xhrRoute) calls.push({ file: file, method: node.arguments[0].value.toUpperCase(), route: xhrRoute });
      return;
    }

    if (HTTP_CALLERS.indexOf(obj.name) === -1) return;
    var method = prop.name;
    if (HTTP_METHODS.indexOf(method) === -1) return;

    var route = routeFromArg(node.arguments[0]);
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

// A set of the PATHS the backend serves, independent of verb, so a path that exists under a
// different method can be told apart from a path that does not exist at all.
var backendPaths = {};
backendRoutes.forEach(function (r) {
  var p = canon(r.route);
  (backendPaths[p] = backendPaths[p] || []).push(r.method);
});

// ---------------------------------------------------------------------------------------------
// 1. missing endpoint  +  1b. wrong HTTP method
//
// EXACT matching. There used to be a "tail heuristic" here: before reporting, it would re-check
// whether ANY backend route contained the call's last path segment, and stay silent if so. It was
// added to cover a controller whose base path the regex might miss — but the base-path parser
// concatenates every string literal inside @RequestMapping(...) and handles this codebase's
// `NotaristConstants.API_BASE_PATH + "/x"` idiom correctly, so the heuristic was insuring against a
// problem that no longer existed while swallowing real ones:
//
//   GET /bundles/{}/documents    tail "documents" matched GET /documents           -> silently PASSED
//   POST /bundles/{}/verification tail "verification" matched POST …/verification/checklist/{} -> PASSED
//
// Both were live-on-flag-flip 404s (a route that does not exist, and a bulk endpoint that does not
// exist), and this check reported neither. A heuristic that turns a real finding into a PASS is worse
// than no check: it is a check that lies. Any base-path parsing gap is now a bug to fix HERE, where
// it is visible, rather than a class of finding to suppress everywhere.
// ---------------------------------------------------------------------------------------------

var calledSet = {};
calls.forEach(function (c) {
  var route = canon(c.route);
  var key = c.method + ' ' + route;
  calledSet[key] = true;
  if (backendSet[key]) return;

  // Is this call gated behind a FEATURES flag? If so it never fires today, so it is a PLANNED
  // integration (the app is written ahead of the endpoint, deliberately) rather than a live break.
  // Heuristic: the enclosing module reads a flag. It is file-level, not statement-level, so it can be
  // over-generous — which is the safe direction: an ungated call to a missing route is the thing that
  // must never be downgraded, and those files reference no flag at all.
  var gated = /FEATURES\./.test(sources[c.file] || '');
  var where = path.relative(APP_ROOT, c.file);

  // The path EXISTS but not under this verb. That is a different bug from a missing endpoint — it is
  // a 405, it is always a frontend mistake, and it is never a "planned integration": the route is
  // right there. So it is blocking regardless of the flag.
  if (backendPaths[route]) {
    report('wrong-method', where,
      c.method + ' ' + c.route + ' — the backend serves this path but only as ' +
      backendPaths[route].join('/') + '. Wrong HTTP method.');
    return;
  }

  report(
    gated ? 'planned-endpoint' : 'missing-endpoint',
    where,
    c.method + ' ' + c.route + (gated
      ? ' — not served by the backend; call is flag-gated, so this is a planned integration'
      : ' — no backend route serves this, and the call is NOT flag-gated'),
  );
});

// ---------------------------------------------------------------------------------------------
// 2. unused endpoint — the backend serves something the app never calls (advisory)
//
// Also exact-matched now, for the same reason. The tail heuristic here was even looser — it ignored
// the METHOD entirely — so any route whose last segment appeared anywhere in any call was considered
// used. This is advisory, so a false PASS costs less; but "the backend built something the app never
// wired up" is precisely what this sprint needs to enumerate, and a check that hides half the list
// cannot do that.
// ---------------------------------------------------------------------------------------------

Object.keys(backendSet).forEach(function (key) {
  if (calledSet[key]) return;
  report('unused-endpoint', path.relative(REPO_ROOT, backendSet[key].file), key + ' — backend serves this; the app never calls it');
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
// A normalizer models a DTO FAMILY, not one record: Verification.js reads VerificationResponse AND
// the ChecklistItemResponse/Progress/Summary records nested inside it. Checking against only the top
// record (which is what this did) meant every nested field — the whole checklist row, every progress
// counter — was outside the check. That is where finding 5 lived: `label`/`required`/`reviewedBy` on
// ChecklistItemResponse, wrong for two sprints, in a file this validator claimed to be checking.
var MODEL_TO_DTO = {
  'Case.js': ['CaseResponse.java'],
  'Bundle.js': ['BundleResponse.java'],
  // Search.js was ABSENT from this map until Sprint 6, and it models the one endpoint that is
  // actually switched on. normalizeCitation had invented an entire vocabulary (score/snippet/... for
  // relevanceScore/citationText/...), so every live search rendered "[1] undefined / skor 0.00 /
  // blank" — and no check looked, because the file was not listed. An unlisted model is an unchecked
  // model; the map is the check's blind spot, so adding to it is the first move when a normalizer
  // gains a DTO.
  'Search.js': ['SearchResponse.java', 'CitationResponse.java'],
  'Timeline.js': ['TimelineResponse.java', 'TimelineEntryResponse.java'],
  'Verification.js': [
    'VerificationResponse.java',
    'ChecklistItemResponse.java',
    'VerificationProgressResponse.java',
    'VerificationSummaryResponse.java',
    'CategoryGroupResponse.java',
  ],
  'Ocr.js': [
    'OcrReviewResponse.java',
    'OcrFieldResponse.java',
    'OcrReviewProgressResponse.java',
    'AuthorityTimelineEntryResponse.java',
  ],
};

/**
 * Keys a normalizer may read that no DTO sends. Two categories, and the split is load-bearing.
 *
 * The first cut of this check had ONE list, and it was wrong in a way worth recording: `label` and
 * `required` were on it as "legacy/fixture spelling", which is true — they ARE the old spellings of
 * title/mandatory. But a single list excuses a key ANYWHERE, so `pick(raw, ['label'])` standing alone
 * was excused too... which is finding 5, the exact bug this check exists to catch. The allowlist
 * silenced the thing it was allegedly allowing an alias for. Verified by re-introducing the bug: the
 * check passed.
 *
 * So an alias is only legitimate NEXT TO a real field. That is what an alias IS.
 */

// Read only alongside a real DTO field. Alone, it is a read of nothing — and a finding.
var ALIAS_ONLY_KEYS = {
  'content': 'Spring Page alias accepted by toList()',
  'items': 'paged-wrapper alias accepted by toList()',
  'id': 'fixture spelling; DTOs use caseId/bundleId/entryId',
  'pages': 'fixture spelling; the DTO sends pageCount',
  'label': 'legacy/fixture spelling; ChecklistItemResponse sends title',
  'required': 'legacy/fixture spelling; ChecklistItemResponse sends mandatory',
  'reviewedBy': 'legacy/fixture spelling; ChecklistItemResponse sends reviewer',
  'authorityItems': 'OcrReviewResponse javadoc names this as the domain-side spelling',
  'at': 'fixture spelling; TimelineEntryResponse sends occurredAt',
  'description': 'fixture spelling; TimelineEntryResponse sends description',
};

// Genuinely frontend-only: no DTO sends it, the normalizer resolves it to null against a real
// backend, and the UI renders that honestly as "—". Each is a KNOWN gap that already fails visibly —
// allowed because of that, not because it is harmless.
var FRONTEND_ONLY_KEYS = {
  'name': 'fixture-only; BundleResponse has no name — null renders as "—" (see the read site)',
  'bundleCount': 'fixture spelling; the DTO sends bundleIds[] and the normalizer counts it',
  'imageUrl': 'frontend-only: no OCR DTO carries a preview image URL',
  'updatedAt': 'frontend-only: Case has no updatedAt; normalizer falls back to createdAt',
  'debtorName': 'frontend-only: the Case aggregate models no debtor (see caseEndpoint blocker)',
  'debitur': 'frontend-only alias of debtorName',
  'bank': 'frontend-only: the Case aggregate models no bank',
  'collateralType': 'frontend-only: the Case aggregate models no collateral',
  'notaris': 'frontend-only: the Case sends assignedNotarisId (a UUID), not a name',
  'completed': 'derived by the normalizer from total-remaining; no DTO sends it',
  'percent': 'computed by the normalizer; no DTO sends it',
  'draftStatus': 'no backend source; explicit-only (mock path), null against the real endpoint',
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
    cleaned = cleaned.replace(/\/\/[^\n]*/g, '').trim();        // drop trailing line comments
    var toks = cleaned.split(/\s+/).filter(Boolean);
    if (toks.length >= 2) names.push(toks[toks.length - 1].replace(/[^\w]/g, ''));
  });
  return names;
}

/** Every string key passed to pick()/toList() in a file: [{ keys: [...], line }] */
function readKeyGroups(file) {
  var groups = [];
  if (!asts[file]) return groups;
  visit(asts[file], function (node) {
    if (node.type !== 'CallExpression' || !node.callee) return;
    if (['pick', 'toList'].indexOf(node.callee.name) === -1) return;
    var arr = node.arguments[1];
    if (!arr || arr.type !== 'ArrayExpression') return;
    var keys = [];
    arr.elements.forEach(function (el) {
      if (el && el.type === 'StringLiteral') keys.push(el.value);
    });
    if (keys.length) groups.push({ keys: keys, line: node.loc ? node.loc.start.line : 0 });
  });
  return groups;
}

Object.keys(MODEL_TO_DTO).forEach(function (modelFile) {
  var modelPath = path.join(SRC, 'models', modelFile);
  if (!asts[modelPath]) return;

  var components = [];   // union across the DTO family
  var byDto = [];        // [{ dto, components }]
  var missingDto = false;
  MODEL_TO_DTO[modelFile].forEach(function (dtoName) {
    var javaFiles = walk(BACKEND, function (n) { return n === dtoName; });
    if (!javaFiles.length) {
      report('dto-mismatch', 'src/models/' + modelFile,
        'no backend DTO named ' + dtoName + ' — normalizer has no contract to check against');
      missingDto = true;
      return;
    }
    var comps = dtoComponents(javaFiles[0]) || [];
    byDto.push({ dto: dtoName, components: comps });
    comps.forEach(function (c) { if (components.indexOf(c) === -1) components.push(c); });
  });
  if (missingDto || !components.length) return;

  // --- 3a. a DTO field NO normalizer field reads = data arriving and being dropped on the floor ---
  //
  // A field is reachable via several idioms — pick(raw,['a']), toList(raw,['entries']), a direct
  // raw.bundleIds, or a CONSUMED list — so matching only pick() reported half the normalizer as
  // blind. This asks the cruder but far more honest question: does the field name appear ANYWHERE
  // in the model source? It can be fooled by a field named in a comment, which is the right way to
  // be wrong: this check exists to catch "the backend added a field and nobody noticed", and a
  // false PASS there is cheap while a false FAIL trains people to ignore the tool.
  var modelSrc = sources[modelPath] || '';
  byDto.forEach(function (entry) {
    entry.components.forEach(function (c) {
      if (!new RegExp('\\b' + c + '\\b').test(modelSrc)) {
        report('dto-mismatch', 'src/models/' + modelFile,
          entry.dto + '.' + c + ' is sent by the backend but no normalizer field reads it');
      }
    });
  });

  // --- 3b. IMPOSSIBLE FIELD ASSUMPTION: a read whose every alias is absent from the DTO family ---
  //
  // The reverse direction, and the one that catches the dangerous class. 3a finds data being dropped;
  // this finds the normalizer reading a field the wire NEVER carries, which resolves to the fallback
  // on every payload forever. `required` defaulting to false is not a blank row — it is the app
  // stating that a MANDATORY legal check is optional.
  //
  // The rule is per-pick(), not per-key: a group is fine if ANY of its aliases is a real DTO field,
  // because that is what a legacy alias IS — `pick(raw, ['title','label'])` reads title and tolerates
  // the old spelling. Only a group where NOTHING matches is a read of pure fiction. That precision is
  // what lets the check stay on without an allowlist entry per alias.
  readKeyGroups(modelPath).forEach(function (g) {
    // A real DTO field in the group -> the read lands, and everything beside it is an alias doing its
    // job.
    var hit = g.keys.some(function (k) { return components.indexOf(k) !== -1; });
    if (hit) return;

    // Nothing real in the group. Only a group made ENTIRELY of documented frontend-only keys is
    // excused. An alias with no real field beside it is precisely the bug (see ALIAS_ONLY_KEYS).
    var excused = g.keys.every(function (k) { return FRONTEND_ONLY_KEYS[k]; });
    if (excused) return;

    var aliases = g.keys.filter(function (k) { return ALIAS_ONLY_KEYS[k]; });
    var hint = aliases.length
      ? ' [' + aliases.join(', ') + '] is a legacy alias — it is only valid NEXT TO the real field ' +
        'it aliases (e.g. pick(raw, [\'title\', \'label\'])), never alone.'
      : '';
    report('impossible-field', 'src/models/' + modelFile + ':' + g.line,
      'reads [' + g.keys.join(', ') + '] — no DTO in ' + MODEL_TO_DTO[modelFile].join('/') +
      ' sends any of these, so this resolves to its fallback on every response.' + hint);
  });
});

// ---------------------------------------------------------------------------------------------
// 3c. ENUM AGREEMENT — every enum literal this app sends must be a member of the Java enum
//
// This is the check that would have caught the sprint's P0. api/search.js sent
// `intentOverride: 'LOOKUP'`; SearchIntent has DOCUMENT_LOOKUP but no LOOKUP; SearchQueryHandler
// passes intentOverride() to IntentClassifier verbatim, so EVERY structured search was a 400. It had
// shipped. Nothing could see it, because no check ever compared a frontend string against a Java
// enum — the two vocabularies only met at runtime, on a real request, against a real backend.
//
// Two mechanisms, deliberately:
//
//   MIRRORS   a frontend array that claims to mirror a Java enum is diffed BOTH ways. A member the
//             Java enum lacks is a 400 waiting to happen; a member the mirror lacks is a value the
//             app cannot handle (GROUNDING_LEVELS was missing UNGROUNDED — the level the backend
//             returns when retrieval finds nothing, i.e. the one that matters most).
//
//   INLINE    a string literal assigned to a known enum-typed request field, checked where it is
//             written. Mirrors only protect vocabularies someone remembered to declare; this
//             catches the next `intentOverride: 'LOOKUP'` at the callsite that writes it.
// ---------------------------------------------------------------------------------------------

/** name -> [members] for every enum declared in the backend. */
function collectJavaEnums() {
  var out = {};
  var javaFiles = walk(BACKEND, function (n) { return /\.java$/.test(n); });
  javaFiles.forEach(function (f) {
    var src = fs.readFileSync(f, 'utf8');

    // Strip comments BEFORE matching, not after. The enum body match below is lazy up to the first
    // `;` or `}`, and a javadoc is full of both — AssistantSafetyMode's first member carries the
    // comment "No unsupported inference; every claim requires a citation; ...", so matching first cut
    // the body off inside that sentence and the enum parsed as EMPTY. An enum that parses as empty
    // reports "no Java enum named X exists", which reads as a missing backend file rather than a
    // broken parser — a validator failing in a way that blames the code it is checking.
    src = src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');

    // `enum Name {` … up to the first `;` or `}`. Handles a nested enum (GroundingScore.Level) too.
    var re = /\benum\s+(\w+)\s*\{([\s\S]*?)(?:;|\})/g;
    var m;
    while ((m = re.exec(src)) !== null) {
      var name = m[1];
      var body = m[2];
      var members = [];
      body.split(',').forEach(function (part) {
        var t = part.trim();
        // A member is an identifier optionally followed by a constructor arg list. Stop at anything
        // that looks like a declaration (has a space-separated type, parens with `)` then more).
        var mm = t.match(/^([A-Z][A-Z0-9_]*)\s*(\(|$)/);
        if (mm) members.push(mm[1]);
      });
      if (members.length) out[name] = { members: members, file: f };
    }
  });
  return out;
}

var javaEnums = collectJavaEnums();

/**
 * Frontend arrays that claim to mirror a Java enum. Each entry names the file, the exported const,
 * and the Java enum it must equal.
 */
var ENUM_MIRRORS = [
  { file: 'src/models/Case.js', name: 'CASE_STATES', enumName: 'CaseState' },
  { file: 'src/models/Search.js', name: 'SEARCH_INTENTS', enumName: 'SearchIntent' },
  { file: 'src/models/Search.js', name: 'GROUNDING_LEVELS', enumName: 'Level' },
  { file: 'src/context/PreferencesContext.js', name: 'SAFETY_MODES', enumName: 'AssistantSafetyMode' },
];

/** The string members of a top-level `export const NAME = ['A','B']` array. */
function arrayConstMembers(file, constName) {
  if (!asts[file]) return null;
  var found = null;
  visit(asts[file], function (node) {
    if (node.type !== 'VariableDeclarator' || !node.id || node.id.name !== constName) return;
    if (!node.init || node.init.type !== 'ArrayExpression') return;
    var vals = [];
    node.init.elements.forEach(function (el) {
      if (el && el.type === 'StringLiteral') vals.push(el.value);
    });
    found = vals;
  });
  return found;
}

ENUM_MIRRORS.forEach(function (mirror) {
  var file = path.join(APP_ROOT, mirror.file);
  var declared = arrayConstMembers(file, mirror.name);
  if (declared === null) {
    report('enum-mismatch', mirror.file,
      mirror.name + ' is declared as this project\'s mirror of Java enum ' + mirror.enumName +
      ' but was not found as an array const — the mirror cannot be checked');
    return;
  }
  var java = javaEnums[mirror.enumName];
  if (!java) {
    report('enum-mismatch', mirror.file,
      'no Java enum named ' + mirror.enumName + ' exists — ' + mirror.name + ' mirrors nothing');
    return;
  }
  declared.forEach(function (v) {
    if (java.members.indexOf(v) === -1) {
      report('enum-mismatch', mirror.file,
        mirror.name + " contains '" + v + "', which is NOT a member of Java enum " + mirror.enumName +
        ' (' + java.members.join(' | ') + '). Sending it is an HTTP 400.');
    }
  });
  java.members.forEach(function (v) {
    if (declared.indexOf(v) === -1) {
      report('enum-mismatch', mirror.file,
        mirror.name + " is missing '" + v + "', which Java enum " + mirror.enumName +
        ' can send. The app cannot handle a value the backend produces.');
    }
  });
});

/**
 * Request fields whose value is deserialized straight into a Java enum, so a literal must be a member.
 *
 * DELIBERATELY NOT LISTED: `decision`. UpdateChecklistItemRequest.decision and
 * ReviewFieldRequest.decision are plain Strings, and FieldDecisionTranslator/DecisionTranslator
 * explicitly accept the frontend vocabulary (APPROVED|REJECTED|NEEDS_CHECK) alongside the domain
 * names. Listing it would flag correct code — the fastest way to get a check ignored.
 */
var REQUEST_ENUM_FIELDS = {
  intentOverride: 'SearchIntent',
  safetyMode: 'AssistantSafetyMode',
  targetState: 'CaseState',
};

Object.keys(asts).forEach(function (file) {
  visit(asts[file], function (node) {
    if (node.type !== 'ObjectProperty' || !node.key) return;
    var key = node.key.name || node.key.value;
    var enumName = REQUEST_ENUM_FIELDS[key];
    if (!enumName) return;

    // Only a bare literal is checkable. `mode === 'x' ? A : null` is handled because the AST visitor
    // reaches the ConditionalExpression's branches as StringLiterals only via this property when the
    // property value IS the literal; a computed value is simply skipped rather than guessed at.
    var vals = [];
    if (node.value && node.value.type === 'StringLiteral') vals.push(node.value);
    if (node.value && node.value.type === 'ConditionalExpression') {
      if (node.value.consequent && node.value.consequent.type === 'StringLiteral') vals.push(node.value.consequent);
      if (node.value.alternate && node.value.alternate.type === 'StringLiteral') vals.push(node.value.alternate);
    }
    if (!vals.length) return;

    var java = javaEnums[enumName];
    if (!java) return;
    vals.forEach(function (lit) {
      if (java.members.indexOf(lit.value) !== -1) return;
      report('enum-mismatch', path.relative(APP_ROOT, file) + ':' + (lit.loc ? lit.loc.start.line : 0),
        "sends " + key + ": '" + lit.value + "' — not a member of Java enum " + enumName +
        ' (' + java.members.join(' | ') + '). This is an HTTP 400 on every call.');
    });
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
// 8. STALE FLAG COMMENT — a comment claiming a route is absent when the backend serves it
//
// `bundleEndpoint: false, // no /bundles` sat in config.js while BundleController served five
// /bundles routes. Nobody re-checked for two sprints, because the comment answered the question.
// A comment that lies about the backend is worse than no comment: it is a cached wrong answer, and
// this is the check that expires the cache.
//
// Only a comment that OPENS with the claim is read — /^no <VERB>? /path/. That anchoring is what
// lets a flag's prose discuss a former claim ("this used to read 'no /bundles', which was FALSE")
// without tripping the check on the words it is correcting.
// ---------------------------------------------------------------------------------------------

function claimedAbsentPaths(comment) {
  var out = [];
  comment.split('\n').forEach(function (raw) {
    var line = raw.replace(/^\s*\*?\s*/, '').trim();
    var m = line.match(/^(?:no|tidak ada)\s+(?:(GET|POST|PUT|PATCH|DELETE)\s+)?(\/[\w/{}.-]+)/i);
    if (m) out.push({ method: m[1] ? m[1].toUpperCase() : null, path: m[2] });
  });
  return out;
}

if (asts[configPath]) {
  // Every flag property, with the line it sits on.
  var flagProps = []; // { flag, line }
  visit(asts[configPath], function (node) {
    if (node.type !== 'VariableDeclarator' || !node.id || node.id.name !== 'FEATURES') return;
    if (!node.init || node.init.type !== 'ObjectExpression') return;
    node.init.properties.forEach(function (p) {
      if (p.type !== 'ObjectProperty' || !p.key || !p.loc) return;
      flagProps.push({ flag: p.key.name || p.key.value, line: p.loc.start.line });
    });
  });

  /**
   * Which flag is a comment ABOUT? Answered by LINE, not by Babel's attachment.
   *
   * Babel attaches `bundleEndpoint: false, // no /bundles` as a TRAILING comment of bundleEndpoint
   * AND a LEADING comment of the next property — so reading both blamed dashboardEndpoint for
   * bundleEndpoint's lie. A finding that points at the wrong line is how a true report gets
   * dismissed as a false one, so attribution is worth getting exactly right:
   *
   *   same line as a flag  -> that flag (a trailing comment)
   *   otherwise            -> the next flag below it (a leading block)
   */
  function ownerOf(comment) {
    if (!comment.loc) return null;
    var start = comment.loc.start.line, end = comment.loc.end.line;
    var sameLine = null, next = null;
    flagProps.forEach(function (fp) {
      if (fp.line === start) sameLine = fp;
      if (fp.line > end && (!next || fp.line < next.line)) next = fp;
    });
    var owner = sameLine || next;
    return owner ? owner.flag : null;
  }

  (asts[configPath].comments || []).forEach(function (c) {
    var claims = claimedAbsentPaths(c.value);
    if (!claims.length) return;
    var flag = ownerOf(c);
    if (!flag) return;
    claims.forEach(function (claim) {
      // Does the backend serve anything at, or under, the path the comment says does not exist?
      var claimed = canon(claim.path);
      var serving = Object.keys(backendSet).filter(function (k) {
        var parts = k.split(' ');
        var verb = parts[0], route = parts[1];
        if (claim.method && verb !== claim.method) return false;
        return route === claimed || route.indexOf(claimed + '/') === 0;
      });
      if (serving.length) {
        report('stale-flag-comment', 'src/constants/config.js:' + c.loc.start.line,
          'FEATURES.' + flag + ' is commented "' + (claim.method ? claim.method + ' ' : '') +
          claim.path + ' does not exist", but the backend serves: ' + serving.join(', ') +
          '. Fix the comment (the flag may still be false for other reasons — say which).');
      }
    });
  });
}

// ---------------------------------------------------------------------------------------------
// 9. RAW ENVELOPE ACCESS — digging `response.data.data` out by hand instead of unwrap()
//
// Every backend response is ApiResponse { status, meta, data, errorCode, errorMessage }, so
// `response.data.data` is the CORRECT payload location — this check is not about the path being
// wrong. It is about who is allowed to walk it.
//
// A hand-rolled dig throws a TypeError the moment a proxy, captive portal or 204 returns a body that
// is not the envelope; it silently skips the 200-with-status:"ERROR" case the backend really does
// emit; and `response.data.data?.items ?? []` bakes in a SHAPE GUESS that fails silently when wrong —
// which is exactly how listBundles returned zero bundles for a payload that was a bare array. One
// unwrapper, one place, one set of guarantees. api/envelope.js owns it.
// ---------------------------------------------------------------------------------------------

Object.keys(asts).forEach(function (file) {
  if (file === path.join(API, 'envelope.js')) return; // the module that defines the unwrapping
  visit(asts[file], function (node) {
    // Match `<x>.data.data`, including through `?.`.
    if (node.type !== 'MemberExpression' && node.type !== 'OptionalMemberExpression') return;
    if (!node.property || (node.property.name || node.property.value) !== 'data') return;
    var inner = node.object;
    if (!inner) return;
    if (inner.type !== 'MemberExpression' && inner.type !== 'OptionalMemberExpression') return;
    if (!inner.property || (inner.property.name || inner.property.value) !== 'data') return;
    report('raw-envelope', path.relative(APP_ROOT, file) + ':' + (node.loc ? node.loc.start.line : 0),
      'reads `.data.data` directly — use unwrap()/unwrapOrThrow() from api/envelope.js, which ' +
      'tolerates a non-envelope body and rejects a 200-with-status:"ERROR"');
  });
});

// ---------------------------------------------------------------------------------------------
// 10. PAGINATION SHAPE — the page descriptor must be read from the real PageInfo
//
// PageResponse<T> { items, page: PageInfo }, and PageInfo answers "is there more" DIRECTLY via
// `hasNext`/`isLast`. api/pagination.js read neither: it looked for `last`, which no backend sends
// (Jackson serializes the record component `isLast` under its own name), so its most authoritative
// rung was dead code that degraded to arithmetic on totalPages. It worked, until a response without a
// total would have stopped every list at page one in silence.
//
// This asserts the pagination module actually reads the fields PageInfo actually sends.
// ---------------------------------------------------------------------------------------------

var PAGINATION_REQUIRED = ['totalPages', 'hasNext', 'isLast'];
var pageInfoFiles = walk(BACKEND, function (n) { return n === 'PageInfo.java'; });
if (pageInfoFiles.length) {
  var pageInfoComponents = dtoComponents(pageInfoFiles[0]) || [];
  var paginationPath = path.join(API, 'pagination.js');
  var paginationSrc = sources[paginationPath] || '';
  if (!paginationSrc) {
    report('pagination-shape', 'src/api/pagination.js', 'module not found — nothing normalizes the page descriptor');
  } else {
    PAGINATION_REQUIRED.forEach(function (fieldName) {
      if (pageInfoComponents.indexOf(fieldName) === -1) return; // PageInfo does not send it; nothing to read
      if (new RegExp("'" + fieldName + "'").test(paginationSrc)) return;
      report('pagination-shape', 'src/api/pagination.js',
        'PageInfo.' + fieldName + ' is sent by the backend but pagination.js never reads it — ' +
        '"is there another page?" is being answered by a weaker signal than the server provides');
    });
  }

  // A list screen that does its own totalPages arithmetic has bypassed the module above. `?? 1` is
  // the specific killer: it turns "the server did not say" into "there is exactly one page".
  //
  // Matched on the AST, NOT on the source text. The first cut of this check regexed the raw source
  // and reported three files — every one of them a comment DESCRIBING the bug it had just been fixed
  // for ("was `data.data?.page?.totalPages ?? 1`"). A checker that cannot tell code from prose about
  // code punishes writing the prose, which is the opposite of what these comments are for.
  Object.keys(asts).forEach(function (file) {
    if (file === paginationPath) return;
    visit(asts[file], function (node) {
      if (node.type !== 'LogicalExpression' || node.operator !== '??') return;
      var left = node.left, right = node.right;
      if (!right || right.type !== 'NumericLiteral' || right.value !== 1) return;
      var reads = left && (left.type === 'MemberExpression' || left.type === 'OptionalMemberExpression')
        && left.property && (left.property.name || left.property.value) === 'totalPages';
      if (!reads) return;
      report('pagination-shape', path.relative(APP_ROOT, file) + ':' + (node.loc ? node.loc.start.line : 0),
        'defaults totalPages to 1 (`totalPages ?? 1`) — a response with no total silently truncates ' +
        'the list at page one. Use hasMorePages() from api/pagination.js.');
    });
  });
}

// ---------------------------------------------------------------------------------------------
// output
// ---------------------------------------------------------------------------------------------

var ORDER = [
  'enum-mismatch', 'wrong-method', 'missing-endpoint', 'impossible-field', 'stale-flag-comment',
  'raw-envelope', 'pagination-shape', 'dto-mismatch', 'duplicate-models', 'dead-service',
  'unreachable-api', 'unused-flag', 'planned-endpoint', 'unused-endpoint',
];
var LABEL = {
  'enum-mismatch': 'Enum mismatch (a literal the Java enum does not contain, or a member the app cannot handle)',
  'wrong-method': 'Wrong HTTP method (the path exists; the verb does not)',
  'missing-endpoint': 'Missing endpoint (ungated call to a route that does not exist)',
  'impossible-field': 'Impossible field assumption (normalizer reads a field no DTO sends)',
  'stale-flag-comment': 'Stale feature-flag comment (claims a route is absent; the backend serves it)',
  'raw-envelope': 'Raw envelope access (hand-rolled .data.data instead of unwrap())',
  'pagination-shape': 'Wrong pagination shape',
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
//
// Everything ELSE blocks, and the Sprint-6 additions block hardest, on purpose:
//
//   enum-mismatch       a guaranteed 400 (this is how 'LOOKUP' shipped)
//   wrong-method        a guaranteed 405; never a "planned integration" — the route is right there
//   impossible-field    a read that silently resolves to its fallback forever ('required' -> false
//                       on MANDATORY legal checks: not missing data, a false statement)
//   stale-flag-comment  a cached wrong answer about the backend
//   raw-envelope        a TypeError on any non-envelope body, and a silent shape guess
//   pagination-shape    a list that stops at page one with no error and no clue
//
// None of these can be "usually fine". Each is a contract violation with a deterministic failure.
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
