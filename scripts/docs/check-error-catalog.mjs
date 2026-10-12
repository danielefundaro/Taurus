#!/usr/bin/env node
/**
 * Verifies the application error catalog (docs/application-errors-standard.md, «Verifica automatica del catalogo").
 *
 * Four sets are compared:
 *
 * 1. Backend keys, from taurus-be/src/main/java:
 *    - the last argument of `new RequestAlertException(…)` (4 or 5 arguments) when it is a constant string: a literal,
 *      a concatenation of literals or a `static final String` constant;
 *    - calls of helper methods returning RequestAlertException: methods of the same file (any visibility), methods of
 *      other classes called as `Class.helper(…)` or statically imported (non-private only). A helper passing one of its
 *      parameters as the key makes the argument at that position a key at each call; a helper with a hardcoded key
 *      (directly or through another helper) throws that key at each of its calls, method references included.
 *      Calls such as `LOG.error(…)` are not helpers;
 *    - the parameters chained to the call: `.with("name", value)`, plus `ErrorArgs.of("name", …).with(…)` arguments;
 *    - enum families (DEFAULT_CONFIG.families): `tenantFeature.{code}.disabled` for TenantFeature,
 *      `inventory.loanSheet.{code}` and `inventory.loanDuration.{code}` for LoanDurationPolicy.DeliveryCode,
 *      `inventory.renewal.{code}` for LoanDurationPolicy.RenewalCode; `{code}` is the first string argument of each
 *      constant. A key expression `"prefix" + … + "suffix"` matching a family template is attributed to the family;
 *      any other non-constant key expression is reported as a warning;
 *    - template issue codes: first argument of `blocking(…)`/`warning(…)` in LoanTemplateValidator and of
 *      `LoanTemplateValidator.blocking(…)`/`warning(…)` in InventoryLoanTemplateService;
 *    - generic keys produced without a literal by ExceptionTranslator and the security handlers (`validation`,
 *      `concurrencyFailure`, `http.{stato}`, `auth.unauthenticated`, `auth.forbidden`): always present.
 * 2. Catalog keys: the section «## Catalogo delle chiavi», every table row whose first cell contains backticked keys,
 *    with the backticked names of the «Parametri» column (text after "`name`:" describes the content of that parameter;
 *    "facoltativo" marks an optional one; "—" means none). Placeholders such as `http.{stato}` match one key segment.
 *    The rows under «### Esiti della verifica del modello» are template issue codes, whose frontend key is
 *    `inventory.loanTemplate.issue.{code}`.
 * 3. Frontend messages: the properties of API_ERROR_MESSAGES in taurus-fe/src/app/constants/api-error-messages.ts.
 *    A string message uses the `{name}` placeholders it contains; a function message uses the parameters it reads
 *    (`args['name']`, `args.name` or destructuring).
 * 4. Frontend behaviors: the keys and `*` patterns of taurus-fe/src/app/constants/api-error-behaviors.ts.
 *
 * Errors (exit code 1):
 * - a backend key or template issue code is missing from the catalog;
 * - a catalog key is not thrown by the backend (generic keys excluded), or a catalog issue code is not produced;
 * - a backend key has no frontend message. Exception: when the «Messaggio» cell of the catalog starts with "Nessuno"
 *   (the frontend applies a behavior and shows no message of its own) or links to the generic texts (#testi-generici),
 *   the message is optional: the frontend falls back to the generic text for the status;
 * - a frontend message key is not in the catalog. PascalCase keys without dots (`NotNull`, `Size`, …) are Bean
 *   Validation constraint codes, documented in the standard but not listed in the catalog tables: they are allowed;
 * - a behavior key or pattern matches no catalog key;
 * - a string message whose placeholders differ from the catalog parameters (required ones must all be used, optional
 *   ones may be), or a function message reading a parameter the catalog does not list;
 * - a throw site whose `.with("name", …)` parameters differ from the catalog parameters (every required parameter,
 *   no unknown one). Each throw site is checked on its own;
 * - a configured enum family, template issue source, the catalog section or a frontend file is missing; duplicated
 *   or malformed catalog keys.
 *
 * Warnings (reported, exit code unchanged): key expressions that cannot be resolved statically, and throw sites whose
 * parameters cannot be resolved statically (a non-literal name, a whole parameter set such as
 * `.with(violation.args())`, or an exception assigned to a variable before being enriched): their parameter check is
 * skipped.
 *
 * Usage: node scripts/docs/check-error-catalog.mjs [--root <repository>]
 */
import path from 'node:path';
import { checkRepository, defaultRepositoryRoot, formatReport } from './error-catalog-lib.mjs';

const args = process.argv.slice(2);
const rootIndex = args.indexOf('--root');
const repositoryRoot = rootIndex >= 0 && args[rootIndex + 1] ? path.resolve(args[rootIndex + 1]) : defaultRepositoryRoot;

try {
    const result = checkRepository(repositoryRoot);
    const report = formatReport(result);
    if (result.errors.length > 0) {
        console.error(report);
        process.exitCode = 1;
    } else {
        console.log(report);
    }
} catch (error) {
    console.error(`check-error-catalog: ${error.stack ?? error.message}`);
    process.exitCode = 2;
}
