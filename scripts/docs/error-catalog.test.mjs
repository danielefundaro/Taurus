import assert from 'node:assert/strict';
import test from 'node:test';
import { DEFAULT_CONFIG, compareErrorCatalog, extractBackend, parseBehaviors, parseCatalog, parseMessages, parseParameterCell } from './error-catalog-lib.mjs';

const CONFIG = {
    ...DEFAULT_CONFIG,
    families: [
        { enumName: 'TenantFeature', file: 'src/com/acme/feature/TenantFeature.java', keys: ['tenantFeature.{code}.disabled'] },
        { enumName: 'DeliveryCode', file: 'src/com/acme/loansheet/LoanDurationPolicy.java', keys: ['inventory.loanSheet.{code}', 'inventory.loanDuration.{code}'] },
    ],
    templateIssues: {
        className: 'LoanTemplateValidator',
        files: ['src/com/acme/loansheet/LoanTemplateValidator.java', 'src/com/acme/loansheet/LoanTemplateService.java'],
        methods: ['blocking', 'warning'],
    },
};

const JAVA = {
    'src/com/acme/service/InventoryService.java': `package com.acme.service;

import org.springframework.http.HttpStatus;

public class InventoryService {
    private static final Logger LOG = LoggerFactory.getLogger(InventoryService.class);
    private static final String ENTITY = "inventory";

    public void assign(int available, int requested) {
        LOG.error("Failed: see \\"error(\\" in the logs", "not.a.key");
        if (requested > available) {
            // throw error(HttpStatus.CONFLICT, "commented out", "commented.key");
            throw error(
                HttpStatus.CONFLICT,
                "Quantity (requested) is not available",
                "inventory.quantity.unavailable"
            ).with("available", available);
        }
        items.findById(1L).orElseThrow(this::notFound);
    }

    public void addPhoto(int count) {
        if (count > 3) throw conflict("Too many photos").with("maximum", 3);
    }

    private RequestAlertException notFound() {
        return error(HttpStatus.NOT_FOUND, "Item not found", "inventory.item.notFound");
    }

    private RequestAlertException conflict(String message) {
        return new RequestAlertException(HttpStatus.CONFLICT, message, ENTITY, "inventory.photo.limit");
    }

    private static RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, ENTITY, key);
    }
}
`,
    'src/com/acme/service/TenantService.java': `package com.acme.service;

class TenantService {
    void requireTenant(String tenant) {
        if (tenant == null) throw new RequestAlertException(URI.create("about:blank"), HttpStatus.FORBIDDEN, "No tenant", "tenant", "auth.tenant.missing");
    }
}
`,
    'src/com/acme/feature/TenantFeature.java': `package com.acme.feature;

public enum TenantFeature {
    FINANCE("finance"),
    INVENTORY("inventory");

    private final String errorKey;

    TenantFeature(String errorKey) {
        this.errorKey = errorKey;
    }

    public String errorKey() {
        return errorKey;
    }
}
`,
    'src/com/acme/feature/TenantFeatureService.java': `package com.acme.feature;

class TenantFeatureService {
    void requireEnabled(TenantFeature feature) {
        throw new RequestAlertException(HttpStatus.FORBIDDEN, "Disabled", "TenantFeature", "tenantFeature." + feature.errorKey() + ".disabled");
    }
}
`,
    'src/com/acme/loansheet/LoanSheetSupport.java': `package com.acme.loansheet;

final class LoanSheetSupport {
    static RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, "loanSheet", key);
    }

    static RequestAlertException notFound(String message) {
        return error(HttpStatus.NOT_FOUND, message, "inventory.loanSheet.notFound");
    }
}
`,
    'src/com/acme/loansheet/LoanSheetService.java': `package com.acme.loansheet;

import static com.acme.loansheet.LoanSheetSupport.notFound;

class LoanSheetService {
    void issue(Sheet sheet, List<ErrorArgs> issues) {
        if (sheet == null) throw notFound("Sheet not found");
        if (sheet.issued()) {
            throw LoanSheetSupport.error(HttpStatus.CONFLICT, "Already issued", "inventory.loanSheet.alreadyIssued")
                .with("sheetNumber", sheet.number());
        }
        if (!issues.isEmpty()) throw LoanSheetSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid", "inventory.loanSheet.validation").with("issues", issues);
    }

    void deliver(Violation violation) {
        throw LoanSheetSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Rules", "inventory.loanDuration." + violation.code()).with(violation.args());
    }
}
`,
    'src/com/acme/loansheet/LoanDurationPolicy.java': `package com.acme.loansheet;

public final class LoanDurationPolicy {
    public enum DeliveryCode {
        /** The item has no "loan type". */
        MISSING_LOAN_TYPE("missingLoanType"),
        EXPIRATION_OUT_OF_RANGE("expirationOutOfRange");

        private final String code;

        DeliveryCode(String code) {
            this.code = code;
        }
    }
}
`,
    'src/com/acme/loansheet/LoanTemplateValidator.java': `package com.acme.loansheet;

final class LoanTemplateValidator {
    List<Issue> validate(String content, String name, Integer line) {
        List<Issue> issues = new ArrayList<>();
        if (content.isEmpty()) issues.add(blocking("empty", line));
        issues.add(blocking("unknownVariable", ErrorArgs.of("name", name).with("list", "goods"), line));
        return issues;
    }

    static Issue blocking(String code, Integer line) {
        return blocking(code, null, line);
    }

    static Issue blocking(String code, ErrorArgs args, Integer line) {
        return new Issue(code, args, line, true);
    }
}
`,
    'src/com/acme/loansheet/LoanTemplateService.java': `package com.acme.loansheet;

class LoanTemplateService {
    void render(List<Issue> issues, int maxMegabytes) {
        issues.add(LoanTemplateValidator.blocking(
            "tooLargePdf",
            ErrorArgs.of("maxMegabytes", maxMegabytes),
            null
        ));
    }
}
`,
};

const ROW = '| --- | --- | --- | --- | --- |';
const CATALOG = `# Standard

## Contratto

| Chiave | Quando si verifica |
| --- | --- |
| \`outside.section\` | Ignored: it precedes the catalog |

## Catalogo delle chiavi

### Chiavi generiche

| Chiave | HTTP | Quando si verifica | Parametri | Messaggio |
${ROW}
| \`validation\` | 400 | Invalid body | — | «Dati non validi» |
| \`concurrencyFailure\` | 409 | Concurrent change | — | «Dati cambiati» |
| \`http.{stato}\` | Lo stato | Any other error | — | [Testo generico](#testi-generici) per stato |
| \`auth.unauthenticated\` | 401 | No token | — | Nessuno: il frontend rinnova la sessione |
| \`auth.forbidden\` | 403 | Role | — | «Permessi insufficienti» |
| \`auth.tenant.missing\` | 403 | No tenant | — | «Nessun tenant» |
| \`tenantFeature.finance.disabled\` | 403 | Finance off | — | «Economia non disponibile» |
| \`tenantFeature.inventory.disabled\` | 403 | Inventory off | — | «Inventario non disponibile» |

### Inventario

| Chiave | HTTP | Quando si verifica | Parametri | Messaggio |
${ROW}
| \`inventory.item.notFound\` | 404 | Missing item | — | «Oggetto non trovato» |
| \`inventory.quantity.unavailable\` | 409 | Not enough | \`available\` | «Disponibili {available}» |
| \`inventory.photo.limit\` | 409 | Too many photos | \`maximum\` | «Massimo {maximum} fotografie» |
| \`inventory.loanSheet.notFound\` | 404 | Missing sheet | — | «Foglio non trovato» |
| \`inventory.loanSheet.alreadyIssued\` | 409 | Already issued | \`sheetNumber\` | «Foglio n. {sheetNumber} già emesso» |
| \`inventory.loanSheet.validation\` | 422 | Duration rules | \`issues\`: elenco con \`assignmentId\`, \`code\` e \`args\`; \`code\` è un codice di \`inventory.loanDuration.*\` | «Emissione non possibile» |
| \`inventory.loanSheet.missingLoanType\` | 422 | No loan type | \`sheetNumber\` | «Senza regime (foglio n. {sheetNumber})» |
| \`inventory.loanSheet.expirationOutOfRange\` | 422 | Out of range | \`minimum\`, \`maximum\`, \`sheetNumber\` | «Fuori intervallo» |
| \`inventory.loanDuration.missingLoanType\` | 422 | No loan type | — | «Senza regime» |
| \`inventory.loanDuration.expirationOutOfRange\` | 422 | Out of range | \`minimum\`, \`maximum\` | «Tra il {minimum} e il {maximum}» |

### Esiti della verifica del modello

| Codice | Blocca | Quando si verifica | Parametri | Messaggio |
${ROW}
| \`empty\` | Sì | Empty file | — | «Il file è vuoto» |
| \`unknownVariable\` | Sì | Unknown variable | \`name\`, \`list\` facoltativo | «Variabile sconosciuta «{name}»» |
| \`tooLargePdf\` | Sì | PDF too large | \`maxMegabytes\` | «Il PDF supera {maxMegabytes} MB» |

## Corrispondenza con le chiavi attuali

| Chiave attuale | Nuova chiave |
| --- | --- |
| \`old.key\` | \`inventory.item.notFound\` |
`;

const MESSAGES = `import { ApiErrorArgs } from '../module/api-error.module';

export type ApiErrorMessage = string | ((args: ApiErrorArgs) => string);

const LIMITS: Record<string, string> = { minAboveMax: 'not a key' };

/** Keep one entry per line. */
export const API_ERROR_MESSAGES: Readonly<Record<string, ApiErrorMessage>> = {
    // Generic keys
    'validation': 'Dati non validi',
    concurrencyFailure: 'Dati cambiati',
    'auth.forbidden': 'Permessi insufficienti',
    'auth.tenant.missing': 'Nessun tenant',
    'tenantFeature.finance.disabled': 'Economia non disponibile',
    'tenantFeature.inventory.disabled': 'Inventario non disponibile',
    'validation.NotNull': 'Campo obbligatorio',
    'validation.Size': (args) => \`Al massimo \${args['max']} caratteri\`,

    'inventory.item.notFound': 'Oggetto non trovato',
    'inventory.quantity.unavailable': 'Quantità insufficiente: disponibili {available}',
    'inventory.photo.limit': 'Massimo {maximum} fotografie',
    'inventory.loanSheet.notFound': 'Foglio non trovato',
    'inventory.loanSheet.alreadyIssued': 'Foglio n. {sheetNumber} già emesso',
    'inventory.loanSheet.validation': (args) => listOf(args['issues']).map((issue) => issue.args['code']).join(', '),
    'inventory.loanSheet.missingLoanType': 'Senza regime (foglio n. {sheetNumber})',
    'inventory.loanSheet.expirationOutOfRange': (args) =>
        \`Fuori intervallo: \${args['minimum']} - \${args.maximum} (foglio n. \${args?.['sheetNumber']})\`,
    'inventory.loanDuration.missingLoanType': 'Senza regime',
    'inventory.loanDuration.expirationOutOfRange': ({ minimum, maximum }) => \`Tra il \${minimum} e il \${maximum}\`,

    'inventory.loanTemplate.issue.empty': 'Il file è vuoto',
    'inventory.loanTemplate.issue.unknownVariable': (args) => \`Variabile «\${args['name']}»\${args['list'] ? ': elenco ' + args['list'] : ''}\`,
    'inventory.loanTemplate.issue.tooLargePdf': 'Il PDF supera ' + '{maxMegabytes} MB'
};
`;

const BEHAVIORS = `export type ApiErrorBehavior = 'renewSession' | 'forbiddenPage' | 'versionConflict';

export const API_ERROR_BEHAVIORS: Readonly<Record<string, ApiErrorBehavior>> = {
    'auth.unauthenticated': 'renewSession',
    'auth.forbidden': 'forbiddenPage',
    concurrencyFailure: 'versionConflict'
};

export const apiErrorBehavior = (key: string): ApiErrorBehavior | undefined => API_ERROR_BEHAVIORS[key];
`;

function run({ java = {}, catalog = CATALOG, messages = MESSAGES, behaviors = BEHAVIORS } = {}) {
    const files = new Map(Object.entries({ ...JAVA, ...java }).filter(([, source]) => source !== null));
    const backend = extractBackend(files, CONFIG);
    return {
        backend,
        result: compareErrorCatalog({
            backend,
            catalog: parseCatalog(catalog, CONFIG),
            messages: messages === null ? null : parseMessages(messages, CONFIG),
            behaviors: behaviors === null ? null : parseBehaviors(behaviors, CONFIG),
            config: CONFIG,
        }),
    };
}

// Adds a row at the end of the Inventario table.
function addRow(row) {
    return CATALOG.replace(/^(\| `inventory\.loanDuration\.expirationOutOfRange` .*)$/m, `$1\n${row}`);
}

function rules(findings, rule) {
    return findings.filter(finding => finding.rule === rule);
}

function sitesOf(backend, key) {
    return backend.sites.filter(site => site.kind === 'literal' && site.key === key);
}

test('accepts a consistent backend, catalog and frontend', () => {
    const { result } = run();
    assert.deepEqual(result.errors, []);
    assert.equal(result.summary.catalogKeys, 18);
    assert.equal(result.summary.catalogIssueCodes, 3);
});

test('resolves keys passed through helpers, across files and lines, ignoring comments, strings and loggers', () => {
    const { backend } = run();
    const keys = new Set(backend.sites.filter(site => site.kind === 'literal').map(site => site.key));
    assert.deepEqual(
        [...keys].sort(),
        [
            'auth.tenant.missing',
            'inventory.item.notFound',
            'inventory.loanSheet.alreadyIssued',
            'inventory.loanSheet.notFound',
            'inventory.loanSheet.validation',
            'inventory.photo.limit',
            'inventory.quantity.unavailable',
        ],
    );
    assert(!keys.has('commented.key') && !keys.has('not.a.key'));
    // The method reference `this::notFound` is a call of the helper with the hardcoded key.
    assert.deepEqual(
        sitesOf(backend, 'inventory.item.notFound').map(site => `${site.file}:${site.line}`),
        ['src/com/acme/service/InventoryService.java:19'],
    );
    // The statically imported helper is resolved in the other file of the package.
    assert.deepEqual(
        sitesOf(backend, 'inventory.loanSheet.notFound').map(site => site.line),
        [7],
    );
    assert.deepEqual(backend.sites.filter(site => site.kind === 'dynamic'), []);
});

test('collects fluent parameters, also for helpers with a hardcoded key', () => {
    const { backend, result } = run();
    assert.deepEqual(sitesOf(backend, 'inventory.quantity.unavailable')[0].names, ['available']);
    assert.deepEqual(sitesOf(backend, 'inventory.photo.limit')[0].names, ['maximum']);
    assert.deepEqual(sitesOf(backend, 'inventory.loanSheet.alreadyIssued')[0].names, ['sheetNumber']);
    // A whole parameter set cannot be resolved statically: warning, no error.
    const unresolved = rules(result.warnings, 'backend-parameters-unresolved');
    assert(unresolved.some(finding => finding.message.includes('with(violation.args())')));
});

test('builds the keys of enum families and reports a missing enum without failing', () => {
    const { backend } = run();
    assert.deepEqual(backend.familyKeys.map(familyKey => familyKey.key).sort(), [
        'inventory.loanDuration.expirationOutOfRange',
        'inventory.loanDuration.missingLoanType',
        'inventory.loanSheet.expirationOutOfRange',
        'inventory.loanSheet.missingLoanType',
        'tenantFeature.finance.disabled',
        'tenantFeature.inventory.disabled',
    ]);
    assert.deepEqual(new Set(backend.sites.filter(site => site.kind === 'family').map(site => site.template)), new Set(['tenantFeature.{code}.disabled', 'inventory.loanDuration.{code}']));

    const { result } = run({ java: { 'src/com/acme/loansheet/LoanDurationPolicy.java': 'package com.acme.loansheet;\n\npublic final class LoanDurationPolicy {}\n' } });
    assert.equal(rules(result.errors, 'family-enum-missing').length, 1);
    assert(rules(result.errors, 'catalog-key-not-thrown').every(finding => finding.message.includes('missing enum DeliveryCode')));
    assert.equal(rules(result.errors, 'catalog-key-not-thrown').length, 4);
});

test('reports a backend key missing from the catalog', () => {
    const catalog = CATALOG.replace(/^\| `inventory\.photo\.limit` .*\n/m, '');
    const { result } = run({ catalog });
    const findings = rules(result.errors, 'backend-key-not-in-catalog');
    assert.equal(findings.length, 1);
    assert.match(findings[0].message, /inventory\.photo\.limit/);
    assert.equal(findings[0].location, 'src/com/acme/service/InventoryService.java:23');
});

test('reports a backend key missing from the frontend messages, unless the catalog shows no message', () => {
    const messages = MESSAGES.replace(/^ {4}'inventory\.item\.notFound'.*\n/m, '');
    const { result } = run({ messages });
    const findings = rules(result.errors, 'frontend-message-missing');
    assert.deepEqual(
        findings.map(finding => finding.message.split(' ')[0]),
        ['`inventory.item.notFound`'],
    );
    // auth.unauthenticated («Nessuno») and http.{stato} (generic text) have no message and are not reported.
    assert(!MESSAGES.includes("'auth.unauthenticated'"));

    const missingFile = run({ messages: null }).result;
    assert.deepEqual(rules(missingFile.errors, 'frontend-message-missing'), []);
});

test('reports an obsolete catalog key and an unknown frontend key, allowing validation constraint codes', () => {
    const catalog = addRow('| `inventory.item.deleted` | 410 | Gone | — | «Eliminato» |');
    const messages = MESSAGES.replace("    'inventory.item.notFound'", "    'inventory.item.archived': 'Archiviato',\n    'inventory.item.notFound'");
    const { result } = run({ catalog, messages });
    assert.deepEqual(
        rules(result.errors, 'catalog-key-not-thrown').map(finding => finding.message),
        ['`inventory.item.deleted` is in the catalog but not thrown by the backend'],
    );
    assert.deepEqual(
        rules(result.errors, 'frontend-message-unknown').map(finding => finding.message),
        ["'inventory.item.archived' is not in the catalog"],
    );
});

test('reports parameter mismatches of backend throw sites and frontend messages', () => {
    const java = {
        'src/com/acme/service/InventoryService.java': JAVA['src/com/acme/service/InventoryService.java']
            .replace('.with("available", available)', '.with("availableQuantity", available)')
            .replace('throw conflict("Too many photos").with("maximum", 3);', 'throw conflict("Too many photos");'),
    };
    const messages = MESSAGES.replace("'Foglio n. {sheetNumber} già emesso'", "'Foglio n. {number} già emesso'").replace("args.maximum", "args['maximumDate']");
    const { result } = run({ java, messages });
    const backend = rules(result.errors, 'backend-parameters').map(finding => finding.message);
    assert.equal(backend.length, 2);
    assert(backend.some(message => message.includes('inventory.quantity.unavailable') && message.includes('missing available') && message.includes('unexpected availableQuantity')));
    assert(backend.some(message => message.includes('inventory.photo.limit') && message.includes('missing maximum')));
    const frontend = rules(result.errors, 'frontend-message-parameters').map(finding => finding.message);
    assert.equal(frontend.length, 2);
    assert(frontend.some(message => message.includes('inventory.loanSheet.alreadyIssued') && message.includes('missing {sheetNumber}') && message.includes('unexpected {number}')));
    assert(frontend.some(message => message.includes('inventory.loanSheet.expirationOutOfRange') && message.includes('maximumDate')));
});

test('skips the parameter check of an exception enriched after being assigned', () => {
    const java = {
        'src/com/acme/service/QuotaService.java': `package com.acme.service;

class QuotaService {
    void check(int available) {
        RequestAlertException problem = new RequestAlertException(HttpStatus.CONFLICT, "No quota", "quota", "inventory.quantity.unavailable");
        problem.with("available", available);
        throw problem;
    }
}
`,
    };
    const { result } = run({ java });
    assert.deepEqual(result.errors, []);
    assert(rules(result.warnings, 'backend-parameters-unresolved').some(finding => finding.message.includes('assigned to a variable')));
});

test('compares template issue codes from both sources with the catalog and the frontend', () => {
    const { backend } = run();
    assert.deepEqual(
        backend.templateSites.map(site => [site.code, site.names]),
        [
            ['empty', []],
            ['unknownVariable', ['name', 'list']],
            ['tooLargePdf', ['maxMegabytes']],
        ],
    );
    const java = {
        'src/com/acme/loansheet/LoanTemplateValidator.java': JAVA['src/com/acme/loansheet/LoanTemplateValidator.java'].replace('blocking("empty", line)', 'blocking("blank", line)'),
    };
    const { result } = run({ java });
    assert.deepEqual(rules(result.errors, 'template-code-not-in-catalog').map(finding => finding.message.split(' ')[3]), ['`blank`']);
    assert.deepEqual(rules(result.errors, 'catalog-code-not-produced').map(finding => finding.message.split(' ')[3]), ['`empty`']);
    assert.deepEqual(
        rules(result.errors, 'frontend-message-missing').map(finding => finding.message.split(' ')[0]),
        ['`inventory.loanTemplate.issue.blank`'],
    );
});

test('reports behavior keys and patterns that match no catalog key', () => {
    const behaviors = BEHAVIORS.replace("concurrencyFailure: 'versionConflict'", "concurrencyFailure: 'versionConflict',\n    'tenant.notProvisioned': 'tenantSetup'").concat(
        "\nexport const API_ERROR_PATTERNS = [{ pattern: 'tenantFeature.*.disabled' }, { patterns: ['*.versionConflict', 'auth.*'] }];\n",
    );
    const { result } = run({ behaviors });
    assert.deepEqual(rules(result.errors, 'behavior-key-unknown').map(finding => finding.message).sort(), ["'*.versionConflict' matches no catalog key", "'tenant.notProvisioned' matches no catalog key"]);
});

test('reads only top-level parameter names of the catalog', () => {
    assert.deepEqual(parseParameterCell('—'), []);
    assert.deepEqual(parseParameterCell('`minimum`, `maximum`'), [
        { name: 'minimum', optional: false },
        { name: 'maximum', optional: false },
    ]);
    assert.deepEqual(parseParameterCell('`issues`: elenco con `assignmentId`, `code` e `args`; `code` è un codice di `inventory.loanDuration.*`'), [{ name: 'issues', optional: false }]);
    assert.deepEqual(parseParameterCell('`problems`: elenco di codici tra `minAboveMax`, `maxAboveTotal`'), [{ name: 'problems', optional: false }]);
    assert.deepEqual(parseParameterCell('`name`, `list` facoltativo'), [
        { name: 'name', optional: false },
        { name: 'list', optional: true },
    ]);
});

test('reports duplicated and out-of-section catalog rows correctly', () => {
    const catalog = addRow('| `inventory.item.notFound` | 404 | Again | — | «Doppio» |');
    const parsed = parseCatalog(catalog, CONFIG);
    assert(!parsed.keys.has('outside.section') && !parsed.keys.has('old.key'));
    assert.deepEqual(parsed.findings.map(finding => finding.rule), ['catalog-duplicate']);
    // A row separated from its table by a blank line is read as the header of a new table.
    const broken = parseCatalog(CATALOG.replace('### Esiti', '| `inventory.item.deleted` | 410 | Gone | — | «Eliminato» |\n\n### Esiti'), CONFIG);
    assert.deepEqual(broken.findings.map(finding => finding.rule), ['catalog-malformed']);
    assert.equal(parsed.keys.get('http.{stato}').messageOptional, true);
    assert(parsed.keys.get('http.{stato}').pattern.test('http.404'));
});
