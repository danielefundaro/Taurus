import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Library behind scripts/docs/check-error-catalog.mjs. The rules are documented in the header of that script.

export const defaultRepositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

export const JAVA_ROOT = 'taurus-be/src/main/java';
export const CATALOG_PATH = 'docs/application-errors-standard.md';
export const MESSAGES_PATH = 'taurus-fe/src/app/constants/api-error-messages.ts';
export const BEHAVIORS_PATH = 'taurus-fe/src/app/constants/api-error-behaviors.ts';

const BASE_PACKAGE = `${JAVA_ROOT}/com/fundaro/zodiac/taurus`;

export const DEFAULT_CONFIG = Object.freeze({
    // Keys produced by ExceptionTranslator and the security handlers without a literal key.
    genericKeys: ['validation', 'concurrencyFailure', 'http.{stato}', 'auth.unauthenticated', 'auth.forbidden'],
    // Keys composed from enum codes: each constant contributes one key per template, `{code}` being its string argument.
    families: [
        {
            enumName: 'TenantFeature',
            file: `${BASE_PACKAGE}/domain/enumeration/TenantFeature.java`,
            keys: ['tenantFeature.{code}.disabled'],
        },
        {
            enumName: 'DeliveryCode',
            file: `${BASE_PACKAGE}/service/loansheet/LoanDurationPolicy.java`,
            keys: ['inventory.loanSheet.{code}', 'inventory.loanDuration.{code}'],
        },
        {
            enumName: 'RenewalCode',
            file: `${BASE_PACKAGE}/service/loansheet/LoanDurationPolicy.java`,
            keys: ['inventory.renewal.{code}'],
        },
    ],
    // Template issue codes: first string argument of `blocking(…)`/`warning(…)`, unqualified inside the validator
    // class and `LoanTemplateValidator.blocking(…)` (or statically imported) in the other files.
    templateIssues: {
        className: 'LoanTemplateValidator',
        files: [`${BASE_PACKAGE}/service/loansheet/LoanTemplateValidator.java`, `${BASE_PACKAGE}/service/loansheet/InventoryLoanTemplateService.java`],
        methods: ['blocking', 'warning'],
    },
    catalogSection: 'Catalogo delle chiavi',
    templateIssueSection: 'Esiti della verifica del modello',
    templateIssuePrefix: 'inventory.loanTemplate.issue.',
    messagesObject: 'API_ERROR_MESSAGES',
    behaviorsObject: 'API_ERROR_BEHAVIORS',
});

const IDENTIFIER = /^[A-Za-z_$][\w$]*$/;
// Bean Validation constraint codes of fieldErrors, as `NotNull` or `validation.NotNull`.
const CONSTRAINT_CODE = /^(?:validation\.)?[A-Z][A-Za-z0-9]*$/;
const CATALOG_KEY = /^[A-Za-z][A-Za-z0-9]*(?:\.(?:[A-Za-z0-9]+|\{[^}]+\}))*$/;
const ISSUE_CODE = /^[a-z][A-Za-z0-9]*(?:\.[a-z][A-Za-z0-9]*)*$/;
const KEY_PROPERTIES = new Set(['key', 'keys', 'pattern', 'patterns', 'errorKey', 'errorKeys', 'match', 'matches']);

// ---------------------------------------------------------------------------------------------------------------------
// Shared helpers

function createLineLocator(source) {
    const starts = [0];
    for (let index = 0; index < source.length; index++) if (source[index] === '\n') starts.push(index + 1);
    return offset => {
        let low = 0;
        let high = starts.length - 1;
        while (low < high) {
            const middle = (low + high + 1) >> 1;
            if (starts[middle] <= offset) low = middle;
            else high = middle - 1;
        }
        return low + 1;
    };
}

function blank(characters, from, to) {
    for (let index = from; index < to; index++) {
        if (characters[index] !== '\n' && characters[index] !== '\r') characters[index] = ' ';
    }
}

function escapeRegExp(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// `http.{stato}` becomes a pattern where each placeholder matches one key segment.
export function catalogKeyPattern(key) {
    if (!key.includes('{')) return null;
    const source = key
        .split(/(\{[^}]+\})/)
        .map(part => (part.startsWith('{') ? '[^.]+' : escapeRegExp(part)))
        .join('');
    return new RegExp(`^${source}$`);
}

// A frontend behavior pattern: `*` matches one or more characters, across segments.
export function behaviorPattern(key) {
    return new RegExp(`^${key.split('*').map(escapeRegExp).join('.+')}$`);
}

// Index of the bracket closing the one at `open`, in text whose literals and comments are blanked.
function matchingBracket(masked, open) {
    let depth = 0;
    for (let index = open; index < masked.length; index++) {
        const character = masked[index];
        if (character === '(' || character === '[' || character === '{') depth++;
        else if (character === ')' || character === ']' || character === '}') {
            depth--;
            if (depth === 0) return index;
        }
    }
    return -1;
}

// Splits the arguments of the call whose opening parenthesis is at `open`. Returns ranges and the index after `)`.
function splitArguments(masked, open) {
    const args = [];
    let depth = 0;
    let start = open + 1;
    for (let index = open; index < masked.length; index++) {
        const character = masked[index];
        if (character === '(' || character === '[' || character === '{') depth++;
        else if (character === ')' || character === ']' || character === '}') {
            depth--;
            if (depth === 0) {
                if (masked.slice(start, index).trim() !== '' || args.length > 0) args.push(trimRange(masked, start, index));
                return { args, end: index + 1 };
            }
        } else if (character === ',' && depth === 1) {
            args.push(trimRange(masked, start, index));
            start = index + 1;
        }
    }
    return null;
}

function trimRange(text, start, end) {
    while (start < end && /\s/.test(text[start])) start++;
    while (end > start && /\s/.test(text[end - 1])) end--;
    return { start, end };
}

function previousSignificant(masked, index) {
    let cursor = index - 1;
    while (cursor >= 0 && /\s/.test(masked[cursor])) cursor--;
    return cursor;
}

function nextSignificant(masked, index) {
    let cursor = index;
    while (cursor < masked.length && /\s/.test(masked[cursor])) cursor++;
    return cursor;
}

// ---------------------------------------------------------------------------------------------------------------------
// Java sources

function scanJavaLiteral(source, start) {
    const delimiter = source.startsWith('"""', start) ? '"""' : source[start];
    let index = start + delimiter.length;
    while (index < source.length) {
        if (source[index] === '\\') {
            index += 2;
            continue;
        }
        if (source.startsWith(delimiter, index)) return { contentStart: start + delimiter.length, contentEnd: index, end: index + delimiter.length };
        if (delimiter.length === 1 && source[index] === '\n') break;
        index++;
    }
    const end = Math.min(index, source.length);
    return { contentStart: start + delimiter.length, contentEnd: end, end };
}

/**
 * Returns two copies of a Java source with the same offsets: `stripped` without comments, and `masked` without
 * comments and without the contents of string and char literals (delimiters are kept). Searches and bracket matching
 * run on `masked`; literal values are read from `stripped`.
 */
export function prepareJava(source) {
    const stripped = source.split('');
    const masked = source.split('');
    let index = 0;
    while (index < source.length) {
        const character = source[index];
        if (character === '/' && source[index + 1] === '/') {
            let stop = source.indexOf('\n', index);
            if (stop < 0) stop = source.length;
            blank(stripped, index, stop);
            blank(masked, index, stop);
            index = stop;
        } else if (character === '/' && source[index + 1] === '*') {
            let stop = source.indexOf('*/', index + 2);
            stop = stop < 0 ? source.length : stop + 2;
            blank(stripped, index, stop);
            blank(masked, index, stop);
            index = stop;
        } else if (character === '"' || character === "'") {
            const literal = scanJavaLiteral(source, index);
            blank(masked, literal.contentStart, literal.contentEnd);
            index = Math.max(literal.end, index + 1);
        } else {
            index++;
        }
    }
    return { stripped: stripped.join(''), masked: masked.join('') };
}

const JAVA_STRING_LITERAL = /^"((?:[^"\\\r\n]|\\.)*)"$/s;

export function javaStringValue(text) {
    const match = JAVA_STRING_LITERAL.exec(text.trim());
    if (!match) return null;
    return match[1].replace(/\\(u+[0-9a-fA-F]{4}|[0-7]{1,3}|.)/g, (_, escape) => {
        if (escape.startsWith('u')) return String.fromCharCode(parseInt(escape.replace(/^u+/, ''), 16));
        if (/^[0-7]/.test(escape)) return String.fromCharCode(parseInt(escape, 8));
        return { n: '\n', t: '\t', r: '\r', b: '\b', f: '\f', s: ' ' }[escape] ?? escape;
    });
}

function createJavaContext(file, source) {
    const { stripped, masked } = prepareJava(source.replace(/^﻿/, ''));
    const packageMatch = /^\s*package\s+([\w.]+)\s*;/m.exec(stripped);
    const imports = new Map();
    const staticImports = [];
    for (const match of stripped.matchAll(/^\s*import\s+(static\s+)?([\w.]+?)(\.\*)?\s*;/gm)) {
        if (match[1]) {
            const parts = match[2].split('.');
            if (match[3]) staticImports.push({ classFqcn: match[2], member: '*' });
            else staticImports.push({ classFqcn: parts.slice(0, -1).join('.'), member: parts.at(-1) });
        } else if (!match[3]) {
            imports.set(match[2].split('.').at(-1), match[2]);
        }
    }
    const constants = new Map();
    for (const match of stripped.matchAll(/\b(?:static\s+final|final\s+static)\s+String\s+([A-Za-z_$][\w$]*)\s*=\s*("(?:[^"\\\r\n]|\\.)*")\s*;/g)) {
        constants.set(match[1], javaStringValue(match[2]));
    }
    const className = path.posix.basename(file, '.java');
    const packageName = packageMatch ? packageMatch[1] : '';
    return {
        file,
        stripped,
        masked,
        line: createLineLocator(stripped),
        className,
        packageName,
        fqcn: packageName ? `${packageName}.${className}` : className,
        imports,
        staticImports,
        constants,
    };
}

function rangeText(context, range) {
    return context.stripped.slice(range.start, range.end).trim();
}

// Splits an expression on the `+` operators at its top level.
function concatenationParts(context, range) {
    const parts = [];
    let depth = 0;
    let start = range.start;
    for (let index = range.start; index < range.end; index++) {
        const character = context.masked[index];
        if (character === '(' || character === '[' || character === '{') depth++;
        else if (character === ')' || character === ']' || character === '}') depth--;
        else if (character === '+' && depth === 0 && context.masked[index + 1] !== '+' && context.masked[index + 1] !== '=' && context.masked[index - 1] !== '+') {
            parts.push(trimRange(context.masked, start, index));
            start = index + 1;
        }
    }
    parts.push(trimRange(context.masked, start, range.end));
    return parts;
}

function createJavaIndex(files) {
    const contexts = [...files].map(([file, source]) => createJavaContext(file, source));
    const byFqcn = new Map(contexts.map(context => [context.fqcn, context]));
    const byFile = new Map(contexts.map(context => [context.file, context]));
    return {
        contexts,
        byFile,
        resolveClass(context, simpleName) {
            if (simpleName === context.className) return context;
            const imported = context.imports.get(simpleName);
            if (imported) return byFqcn.get(imported) ?? null;
            return byFqcn.get(context.packageName ? `${context.packageName}.${simpleName}` : simpleName) ?? null;
        },
        byFqcn,
    };
}

// Value of a constant string expression: literals, concatenations of literals and `static final String` constants.
function constantString(index, context, range) {
    const values = [];
    for (const part of concatenationParts(context, range)) {
        const value = constantPart(index, context, rangeText(context, part));
        if (value === null) return null;
        values.push(value);
    }
    return values.join('');
}

function constantPart(index, context, text) {
    const literal = javaStringValue(text);
    if (literal !== null) return literal;
    if (IDENTIFIER.test(text) && context.constants.has(text)) return context.constants.get(text);
    const qualified = /^([A-Z][\w$]*)\s*\.\s*([A-Za-z_$][\w$]*)$/.exec(text);
    if (qualified) {
        const target = index.resolveClass(context, qualified[1]);
        if (target?.constants.has(qualified[2])) return target.constants.get(qualified[2]);
    }
    return null;
}

// `"prefix." + code + ".suffix"`: the constant prefix and suffix around a non-constant middle.
function concatenationShape(index, context, range) {
    const parts = concatenationParts(context, range).map(part => constantPart(index, context, rangeText(context, part)));
    if (!parts.some(value => value === null)) return null;
    let prefix = '';
    let suffix = '';
    let first = 0;
    while (first < parts.length && parts[first] !== null) prefix += parts[first++];
    let last = parts.length - 1;
    while (last >= 0 && parts[last] !== null) suffix = parts[last--] + suffix;
    return { prefix, suffix };
}

// Parses `.with("name", value)` calls chained from `from`. Non-literal names make the parameters unresolved.
function parseWithChain(context, from) {
    const names = [];
    const unresolved = [];
    const pattern = /\s*\.\s*with\s*\(/y;
    let cursor = from;
    for (;;) {
        pattern.lastIndex = cursor;
        const match = pattern.exec(context.masked);
        if (!match) break;
        const call = splitArguments(context.masked, cursor + match[0].length - 1);
        if (!call) break;
        const nameText = call.args[0] ? rangeText(context, call.args[0]).replace(/\s+/g, ' ') : '';
        const name = call.args.length === 2 ? javaStringValue(nameText) : null;
        if (name === null) unresolved.push(call.args.length === 2 ? `with(${nameText}, …)` : `with(${nameText})`);
        else names.push(name);
        cursor = call.end;
    }
    return { names, unresolved, end: cursor };
}

// Parameter names passed as an ErrorArgs argument: `ErrorArgs.of("a", x).with("b", y)` or `new ErrorArgs().with(…)`.
function errorArgsNames(context, range) {
    const names = [];
    const unresolved = [];
    let cursor;
    const created = /new\s+ErrorArgs\s*\(\s*\)/y;
    const factory = /ErrorArgs\s*\.\s*of\s*\(/y;
    created.lastIndex = range.start;
    factory.lastIndex = range.start;
    if (created.exec(context.masked)) {
        cursor = created.lastIndex;
    } else if (factory.exec(context.masked)) {
        const call = splitArguments(context.masked, factory.lastIndex - 1);
        if (!call) return null;
        const name = call.args.length === 2 ? javaStringValue(rangeText(context, call.args[0])) : null;
        if (name === null) unresolved.push('ErrorArgs.of(…)');
        else names.push(name);
        cursor = call.end;
    } else {
        return null;
    }
    const chain = parseWithChain(context, cursor);
    return { names: [...names, ...chain.names], unresolved: [...unresolved, ...chain.unresolved] };
}

function findHelpers(index) {
    const helpers = [];
    const declaration = /(?<![\w$.])((?:(?:public|protected|private|static|final|synchronized)\s+)*)RequestAlertException\s+([A-Za-z_$][\w$]*)\s*\(/g;
    for (const context of index.contexts) {
        for (const match of context.masked.matchAll(declaration)) {
            const open = match.index + match[0].length - 1;
            const parameters = splitArguments(context.masked, open);
            if (!parameters) continue;
            const body = /\s*(?:throws\s+[\w$.,\s]+)?\{/y;
            body.lastIndex = parameters.end;
            if (!body.exec(context.masked)) continue;
            const bodyStart = body.lastIndex - 1;
            const bodyEnd = matchingBracket(context.masked, bodyStart);
            if (bodyEnd < 0) continue;
            const params = parameters.args.map(range => {
                const text = rangeText(context, range).replace(/@[\w$.]+(?:\([^)]*\))?/g, ' ');
                return /([A-Za-z_$][\w$]*)\s*$/.exec(text)?.[1] ?? '';
            });
            helpers.push({
                context,
                name: match[2],
                params,
                isPrivate: /\bprivate\b/.test(match[1]),
                nameIndex: match.index + match[0].lastIndexOf(match[2]),
                bodyStart,
                bodyEnd,
                calls: [],
                callSites: 0,
                analysis: null,
            });
        }
    }
    return helpers;
}

function resolveHelper(index, helpersByContext, context, qualifier, name, arity) {
    const pick = (target, nonPrivateOnly) =>
        (helpersByContext.get(target) ?? []).find(helper => helper.name === name && (arity === null || helper.params.length === arity) && !(nonPrivateOnly && helper.isPrivate));
    if (!qualifier || qualifier === 'this' || qualifier === context.className) {
        const own = pick(context, false);
        if (own || qualifier) return own ?? null;
        for (const staticImport of context.staticImports) {
            if (staticImport.member !== '*' && staticImport.member !== name) continue;
            const target = index.byFqcn.get(staticImport.classFqcn);
            const imported = target ? pick(target, true) : null;
            if (imported) return imported;
        }
        return null;
    }
    const target = index.resolveClass(context, qualifier);
    return target ? (pick(target, true) ?? null) : null;
}

function enclosingHelper(helpersByContext, context, position) {
    return (helpersByContext.get(context) ?? []).find(helper => helper.bodyStart < position && position < helper.bodyEnd) ?? null;
}

// Finds `new RequestAlertException(…)`, helper calls and helper method references, with their `.with(…)` chains.
function findCalls(index, helpers) {
    const helpersByContext = new Map();
    for (const helper of helpers) {
        if (!helpersByContext.has(helper.context)) helpersByContext.set(helper.context, []);
        helpersByContext.get(helper.context).push(helper);
    }
    const helperNames = [...new Set(helpers.map(helper => helper.name))];
    const declarations = new Set(helpers.map(helper => `${helper.context.file}:${helper.nameIndex}`));
    const calls = [];
    const callPattern = helperNames.length
        ? new RegExp(`(?<![\\w$.])(?:(this|[A-Z][\\w$]*)\\s*\\.\\s*)?(${helperNames.map(escapeRegExp).join('|')})\\s*\\(`, 'g')
        : null;
    const referencePattern = helperNames.length ? new RegExp(`(?<![\\w$.])(this|[A-Z][\\w$]*)\\s*::\\s*(${helperNames.map(escapeRegExp).join('|')})\\b`, 'g') : null;

    for (const context of index.contexts) {
        const register = (call, range) => {
            call.context = context;
            call.enclosing = enclosingHelper(helpersByContext, context, call.start);
            call.line = context.line(call.start);
            const previous = previousSignificant(context.masked, call.start);
            call.assigned = previous >= 0 && context.masked[previous] === '=' && !'=!<>'.includes(context.masked[previous - 1] ?? '');
            if (range) {
                call.chain = parseWithChain(context, range.end);
                call.argNames = { names: [], unresolved: [] };
                for (const argument of range.args) {
                    const names = errorArgsNames(context, argument);
                    if (names) {
                        call.argNames.names.push(...names.names);
                        call.argNames.unresolved.push(...names.unresolved);
                    }
                }
            } else {
                call.chain = { names: [], unresolved: [] };
                call.argNames = { names: [], unresolved: [] };
            }
            calls.push(call);
            if (call.enclosing) call.enclosing.calls.push(call);
        };

        for (const match of context.masked.matchAll(/(?<![\w$.])new\s+RequestAlertException\s*\(/g)) {
            const split = splitArguments(context.masked, match.index + match[0].length - 1);
            if (!split) continue;
            register({ kind: 'new', start: match.index, args: split.args }, split);
        }
        if (callPattern) {
            for (const match of context.masked.matchAll(callPattern)) {
                if (declarations.has(`${context.file}:${match.index + match[0].lastIndexOf(match[2])}`)) continue;
                const split = splitArguments(context.masked, match.index + match[0].length - 1);
                if (!split) continue;
                // A method declaration with the same name, not a call.
                if (/^\s*(?:throws\b[^{;]*)?\{/.test(context.masked.slice(split.end, split.end + 200))) continue;
                const helper = resolveHelper(index, helpersByContext, context, match[1], match[2], split.args.length);
                if (!helper) continue;
                register({ kind: 'helper', helper, start: match.index, args: split.args }, split);
            }
            for (const match of context.masked.matchAll(referencePattern)) {
                const helper = resolveHelper(index, helpersByContext, context, match[1], match[2], null);
                if (!helper) continue;
                register({ kind: 'reference', helper, start: match.index, args: [], text: match[0].replace(/\s+/g, '') }, null);
            }
        }
    }
    for (const call of calls) {
        if (call.helper && call.enclosing !== call.helper) call.helper.callSites++;
    }
    return calls;
}

function classifyKey(index, call, range, families) {
    const context = call.context;
    const value = constantString(index, context, range);
    if (value !== null) return { kind: 'literal', key: value };
    const text = rangeText(context, range);
    if (call.enclosing && IDENTIFIER.test(text) && call.enclosing.params.includes(text)) {
        return { kind: 'parameter', index: call.enclosing.params.indexOf(text) };
    }
    const shape = concatenationShape(index, context, range);
    if (shape) {
        for (const family of families) {
            for (const template of family.keys) {
                const [prefix, suffix] = template.split('{code}');
                if (shape.prefix === prefix && shape.suffix === suffix) return { kind: 'family', family, template };
            }
        }
    }
    return { kind: 'dynamic', text: text.replace(/\s+/g, ' ') };
}

function callKeyRanges(call, analysis) {
    if (call.kind === 'new') {
        if (call.args.length !== 4 && call.args.length !== 5) return [];
        return [call.args.at(-1)];
    }
    if (!analysis || analysis.keyIndexes.size === 0) return [];
    return [...analysis.keyIndexes].map(position => call.args[position]).filter(Boolean);
}

function analyzeHelper(index, helper, families, visiting = new Set()) {
    if (helper.analysis) return helper.analysis;
    const analysis = { keyIndexes: new Set(), carried: new Set(), unresolved: [], inner: [] };
    if (visiting.has(helper)) return analysis;
    visiting.add(helper);
    for (const call of helper.calls) {
        const nested = call.helper ? analyzeHelper(index, call.helper, families, visiting) : null;
        if (call.kind === 'reference') {
            if (nested.keyIndexes.size === 0) analysis.inner.push(call);
            continue;
        }
        if (call.kind === 'helper' && nested.keyIndexes.size === 0) {
            analysis.inner.push(call);
            continue;
        }
        let parameter = false;
        for (const range of callKeyRanges(call, nested)) {
            const key = classifyKey(index, call, range, families);
            if (key.kind === 'parameter') {
                parameter = true;
                analysis.keyIndexes.add(key.index);
            }
        }
        if (parameter) {
            for (const name of [...call.chain.names, ...call.argNames.names, ...(nested?.carried ?? [])]) analysis.carried.add(name);
            analysis.unresolved.push(...call.chain.unresolved, ...call.argNames.unresolved, ...(nested?.unresolved ?? []));
        } else {
            analysis.inner.push(call);
        }
    }
    visiting.delete(helper);
    helper.analysis = analysis;
    return analysis;
}

// Expands a call into thrown keys. Calls of helpers with a hardcoded key expand into the calls inside the helper,
// keeping the outer call as the location and adding its `.with(…)` parameters.
function expandCall(index, call, families, origin = call, inherited = null, stack = new Set()) {
    const names = [...(inherited?.names ?? []), ...call.chain.names, ...call.argNames.names];
    const unresolved = [...(inherited?.unresolved ?? []), ...call.chain.unresolved, ...call.argNames.unresolved];
    if (call === origin && call.assigned) unresolved.push('the exception is assigned to a variable');
    const via = [...(inherited?.via ?? [])];
    const analysis = call.helper ? analyzeHelper(index, call.helper, families) : null;
    const site = (key, extraNames = [], extraUnresolved = []) => ({
        ...key,
        file: origin.context.file,
        line: origin.line,
        names: [...names, ...extraNames],
        unresolved: [...unresolved, ...extraUnresolved],
        via,
    });

    if (call.kind === 'new') {
        return callKeyRanges(call, null).map(range => site(classifyKey(index, call, range, families))).filter(result => result.kind !== 'parameter');
    }
    if (analysis.keyIndexes.size > 0) {
        if (call.kind === 'reference') return [site({ kind: 'dynamic', text: call.text })];
        return callKeyRanges(call, analysis)
            .map(range => site(classifyKey(index, call, range, families), [...analysis.carried], analysis.unresolved))
            .filter(result => result.kind !== 'parameter');
    }
    const results = [];
    for (const inner of analysis.inner) {
        if (stack.has(inner)) continue;
        const nextStack = new Set(stack).add(inner);
        results.push(...expandCall(index, inner, families, origin, { names, unresolved, via: [...via, call.helper.name] }, nextStack));
    }
    return results;
}

function parseEnumConstants(context, enumName) {
    const match = new RegExp(`\\benum\\s+${escapeRegExp(enumName)}\\b[^{;]*\\{`).exec(context.masked);
    if (!match) return null;
    const open = match.index + match[0].length - 1;
    const close = matchingBracket(context.masked, open);
    if (close < 0) return null;
    const segments = [];
    let depth = 0;
    let start = open + 1;
    let index = open + 1;
    for (; index < close; index++) {
        const character = context.masked[index];
        if (character === '(' || character === '[' || character === '{') depth++;
        else if (character === ')' || character === ']' || character === '}') depth--;
        else if (depth === 0 && (character === ',' || character === ';')) {
            segments.push(trimRange(context.masked, start, index));
            start = index + 1;
            if (character === ';') break;
        }
    }
    if (index >= close) segments.push(trimRange(context.masked, start, close));
    const constants = [];
    for (const segment of segments) {
        const text = context.masked.slice(segment.start, segment.end).replace(/@[\w$.]+(?:\([^)]*\))?/g, m => ' '.repeat(m.length));
        const name = /^\s*([A-Z_$][\w$]*)/.exec(text);
        if (!name) continue;
        const nameEnd = segment.start + name.index + name[0].length;
        const open = nextSignificant(context.masked, nameEnd);
        let code = null;
        if (context.masked[open] === '(') {
            const split = splitArguments(context.masked, open);
            for (const argument of split?.args ?? []) {
                const value = javaStringValue(rangeText(context, argument));
                if (value !== null) {
                    code = value;
                    break;
                }
            }
        }
        constants.push({ name: name[1], code, line: context.line(segment.start) });
    }
    return { constants, line: context.line(match.index) };
}

const JAVA_KEYWORDS = new Set(['if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return', 'new', 'throw', 'else', 'case', 'try', 'do']);

// Parameter names of the innermost method declaration whose body contains `position`.
function enclosingMethodParameters(context, position) {
    if (!context.methods) {
        context.methods = [];
        for (const match of context.masked.matchAll(/(?<![\w$.])([A-Za-z_$][\w$]*)\s+([A-Za-z_$][\w$]*)\s*\(/g)) {
            if (JAVA_KEYWORDS.has(match[1]) || JAVA_KEYWORDS.has(match[2])) continue;
            const parameters = splitArguments(context.masked, match.index + match[0].length - 1);
            if (!parameters) continue;
            const body = /\s*(?:throws\s+[\w$.,\s]+)?\{/y;
            body.lastIndex = parameters.end;
            if (!body.exec(context.masked)) continue;
            const bodyStart = body.lastIndex - 1;
            const bodyEnd = matchingBracket(context.masked, bodyStart);
            if (bodyEnd < 0) continue;
            const names = parameters.args.map(range => /([A-Za-z_$][\w$]*)\s*$/.exec(rangeText(context, range))?.[1] ?? '');
            context.methods.push({ bodyStart, bodyEnd, names });
        }
    }
    let innermost = null;
    for (const method of context.methods) {
        if (method.bodyStart < position && position < method.bodyEnd && (!innermost || method.bodyStart > innermost.bodyStart)) innermost = method;
    }
    return innermost?.names ?? [];
}

function extractTemplateIssues(index, context, settings) {
    const sites = [];
    const findings = [];
    const methods = settings.methods.map(escapeRegExp).join('|');
    const pattern = new RegExp(`(?<![\\w$.])(?:(${escapeRegExp(settings.className)})\\s*\\.\\s*)?(${methods})\\s*\\(`, 'g');
    const ownClass = context.className === settings.className;
    const imported = new Set(
        context.staticImports.filter(staticImport => staticImport.classFqcn.split('.').at(-1) === settings.className).map(staticImport => staticImport.member),
    );
    for (const match of context.masked.matchAll(pattern)) {
        const method = match[2];
        if (!match[1] && !ownClass && !imported.has('*') && !imported.has(method)) continue;
        const split = splitArguments(context.masked, match.index + match[0].length - 1);
        if (!split) continue;
        if (/^\s*(?:throws\b[^{;]*)?\{/.test(context.masked.slice(split.end, split.end + 200))) continue;
        const line = context.line(match.index);
        const codeText = split.args[0] ? rangeText(context, split.args[0]) : '';
        const code = constantPart(index, context, codeText);
        // An overload delegating its own `code` parameter, such as `blocking(code, null, line)`.
        if (code === null && IDENTIFIER.test(codeText) && enclosingMethodParameters(context, match.index).includes(codeText)) continue;
        if (code === null) {
            findings.push({ rule: 'template-code-dynamic', file: context.file, line, message: `${method}(${codeText.replace(/\s+/g, ' ')}, …) has a non-literal issue code` });
            continue;
        }
        const chain = parseWithChain(context, split.end);
        const names = [...chain.names];
        const unresolved = [...chain.unresolved];
        for (const argument of split.args) {
            const argNames = errorArgsNames(context, argument);
            if (argNames) {
                names.push(...argNames.names);
                unresolved.push(...argNames.unresolved);
            }
        }
        sites.push({ code, file: context.file, line, names, unresolved, method });
    }
    return { sites, findings };
}

/**
 * Extracts the backend error keys from Java sources.
 * @param {Map<string, string>} files repository-relative path → source
 */
export function extractBackend(files, config = DEFAULT_CONFIG) {
    const index = createJavaIndex(files);
    const helpers = findHelpers(index);
    const calls = findCalls(index, helpers);
    const families = config.families;
    const sites = [];
    for (const helper of helpers) analyzeHelper(index, helper, families);
    for (const call of calls) {
        const helper = call.enclosing;
        if (helper && helper.analysis.keyIndexes.size === 0 && helper.callSites > 0 && helper.analysis.inner.includes(call)) continue;
        sites.push(...expandCall(index, call, families));
    }

    const findings = [];
    const familyKeys = [];
    const missingFamilies = new Set();
    for (const family of families) {
        const context = index.byFile.get(family.file);
        const keysText = family.keys.join(', ');
        if (!context) {
            findings.push({ rule: 'family-enum-missing', file: family.file, message: `file not found: cannot read enum ${family.enumName} for keys ${keysText}` });
            missingFamilies.add(family);
            continue;
        }
        const parsed = parseEnumConstants(context, family.enumName);
        if (!parsed) {
            findings.push({ rule: 'family-enum-missing', file: family.file, message: `enum ${family.enumName} not found: cannot verify keys ${keysText}` });
            missingFamilies.add(family);
            continue;
        }
        if (parsed.constants.length === 0) findings.push({ rule: 'family-enum-empty', file: family.file, line: parsed.line, message: `enum ${family.enumName} declares no constants` });
        for (const constant of parsed.constants) {
            if (constant.code === null) {
                findings.push({ rule: 'family-code-missing', file: family.file, line: constant.line, message: `${family.enumName}.${constant.name} has no string code argument` });
                continue;
            }
            for (const template of family.keys) {
                familyKeys.push({ key: template.replace('{code}', constant.code), family: family.enumName, constant: constant.name, file: family.file, line: constant.line });
            }
        }
    }

    const templateSites = [];
    for (const file of config.templateIssues.files) {
        const context = index.byFile.get(file);
        if (!context) {
            findings.push({ rule: 'template-source-missing', file, message: 'file not found: cannot read template issue codes' });
            continue;
        }
        const extracted = extractTemplateIssues(index, context, config.templateIssues);
        templateSites.push(...extracted.sites);
        findings.push(...extracted.findings);
    }

    return {
        files: index.contexts.length,
        sites,
        familyKeys,
        templateSites,
        findings,
        families: families.map(family => ({ ...family, missing: missingFamilies.has(family) })),
    };
}

// ---------------------------------------------------------------------------------------------------------------------
// Catalog (Markdown)

function splitCells(line) {
    let text = line.trim();
    if (text.startsWith('|')) text = text.slice(1);
    if (text.endsWith('|') && !text.endsWith('\\|')) text = text.slice(0, -1);
    return text.split(/(?<!\\)\|/).map(cell => cell.trim());
}

// Top-level parameter names of a «Parametri» cell. Text after "`name`:" describes the content of that parameter.
export function parseParameterCell(cell) {
    const text = (cell ?? '').trim();
    if (text === '' || /^[—–-]$/.test(text)) return [];
    const description = /`[^`]+`\s*:/.exec(text);
    const relevant = description ? text.slice(0, description.index + description[0].length - 1) : text;
    const parameters = [];
    for (const match of relevant.matchAll(/`([^`]+)`([^`,;]*)/g)) {
        if (!IDENTIFIER.test(match[1])) continue;
        parameters.push({ name: match[1], optional: /facoltativ/i.test(match[2]) });
    }
    return parameters;
}

function isOptionalMessage(cell) {
    const text = (cell ?? '').trim();
    return /^Nessun[oa]?\b/i.test(text) || text.includes('(#testi-generici)');
}

/**
 * Parses the «Catalogo delle chiavi» section: every table row whose first cell holds backticked keys.
 */
export function parseCatalog(markdown, config = DEFAULT_CONFIG) {
    const lines = markdown.replace(/^﻿/, '').split(/\r?\n/);
    const keys = new Map();
    const issueCodes = new Map();
    const findings = [];
    const start = lines.findIndex(line => line.trim() === `## ${config.catalogSection}`);
    if (start < 0) {
        findings.push({ rule: 'catalog-section-missing', message: `section "## ${config.catalogSection}" not found` });
        return { keys, issueCodes, findings };
    }
    let heading = '';
    let table = null;
    for (let index = start + 1; index < lines.length; index++) {
        const line = lines[index];
        if (/^##\s/.test(line)) break;
        const headingMatch = /^###\s+(.*)$/.exec(line);
        if (headingMatch) {
            heading = headingMatch[1].trim();
            table = null;
            continue;
        }
        if (!line.trim().startsWith('|')) {
            table = null;
            continue;
        }
        const cells = splitCells(line);
        if (!table) {
            // A row read as a table header would be silently ignored.
            if (cells[0].includes('`')) findings.push({ rule: 'catalog-malformed', line: index + 1, message: `row ${cells[0]} is a table header: the table is broken or has no header` });
            table = {
                parameters: cells.findIndex(cell => /^Parametri$/i.test(cell)),
                message: cells.findIndex(cell => /^Messaggio$/i.test(cell)),
                issues: heading === config.templateIssueSection,
            };
            continue;
        }
        if (cells.every(cell => /^:?-{3,}:?$/.test(cell))) continue;
        const names = [...cells[0].matchAll(/`([^`]+)`/g)].map(match => match[1]);
        if (names.length === 0) continue;
        const entry = {
            line: index + 1,
            section: heading,
            parameters: table.parameters >= 0 ? parseParameterCell(cells[table.parameters]) : [],
            messageOptional: table.message >= 0 ? isOptionalMessage(cells[table.message]) : false,
        };
        if (table.parameters < 0) findings.push({ rule: 'catalog-malformed', line: index + 1, message: `table under "${heading}" has no Parametri column` });
        for (const name of names) {
            const target = table.issues ? issueCodes : keys;
            const valid = table.issues ? ISSUE_CODE.test(name) : CATALOG_KEY.test(name);
            if (!valid) {
                findings.push({ rule: 'catalog-malformed', line: index + 1, message: `${table.issues ? 'issue code' : 'key'} \`${name}\` is malformed` });
                continue;
            }
            if (target.has(name)) {
                findings.push({ rule: 'catalog-duplicate', line: index + 1, message: `\`${name}\` is already listed at line ${target.get(name).line}` });
                continue;
            }
            target.set(name, { ...entry, key: name, pattern: table.issues ? null : catalogKeyPattern(name) });
        }
    }
    return { keys, issueCodes, findings };
}

// ---------------------------------------------------------------------------------------------------------------------
// TypeScript sources

function decodeJsString(text) {
    return text.replace(/\\(u\{[0-9a-fA-F]+\}|u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|\r?\n|.)/g, (_, escape) => {
        if (escape.startsWith('u{')) return String.fromCodePoint(parseInt(escape.slice(2, -1), 16));
        if (escape.startsWith('u') && escape.length === 5) return String.fromCharCode(parseInt(escape.slice(1), 16));
        if (escape.startsWith('x') && escape.length === 3) return String.fromCharCode(parseInt(escape.slice(1), 16));
        if (escape.endsWith('\n')) return '';
        return { n: '\n', t: '\t', r: '\r', b: '\b', f: '\f', v: '\v', 0: '\0' }[escape] ?? escape;
    });
}

function scanTemplateLiteral(source, start) {
    let index = start + 1;
    let expressions = false;
    while (index < source.length) {
        const character = source[index];
        if (character === '\\') index += 2;
        else if (character === '`') return { end: index + 1, expressions };
        else if (character === '$' && source[index + 1] === '{') {
            expressions = true;
            index = scanCodeUntilBrace(source, index + 2);
        } else index++;
    }
    return { end: source.length, expressions };
}

function scanCodeUntilBrace(source, start) {
    let depth = 0;
    let index = start;
    while (index < source.length) {
        const character = source[index];
        if (character === "'" || character === '"') index = scanQuoted(source, index);
        else if (character === '`') index = scanTemplateLiteral(source, index).end;
        else if (character === '/' && source[index + 1] === '/') index = source.indexOf('\n', index) < 0 ? source.length : source.indexOf('\n', index);
        else if (character === '/' && source[index + 1] === '*') index = source.indexOf('*/', index + 2) < 0 ? source.length : source.indexOf('*/', index + 2) + 2;
        else if (character === '{') {
            depth++;
            index++;
        } else if (character === '}') {
            if (depth === 0) return index + 1;
            depth--;
            index++;
        } else index++;
    }
    return source.length;
}

function scanQuoted(source, start) {
    const quote = source[start];
    let index = start + 1;
    while (index < source.length) {
        if (source[index] === '\\') index += 2;
        else if (source[index] === quote) return index + 1;
        else if (source[index] === '\n') return index;
        else index++;
    }
    return source.length;
}

function scanRegExp(source, start) {
    let index = start + 1;
    let inClass = false;
    while (index < source.length) {
        const character = source[index];
        if (character === '\\') index += 2;
        else if (character === '\n') return index;
        else if (character === '[') {
            inClass = true;
            index++;
        } else if (character === ']') {
            inClass = false;
            index++;
        } else if (character === '/' && !inClass) {
            index++;
            while (/[a-z]/i.test(source[index] ?? '')) index++;
            return index;
        } else index++;
    }
    return source.length;
}

/**
 * Masks a TypeScript source like prepareJava and lists its string literals with their decoded values.
 */
export function prepareTypeScript(source) {
    const text = source.replace(/^﻿/, '');
    const masked = text.split('');
    const literals = [];
    let previous = '';
    let previousWord = '';
    let index = 0;
    while (index < text.length) {
        const character = text[index];
        const next = text[index + 1];
        if (character === '/' && next === '/') {
            const stop = text.indexOf('\n', index) < 0 ? text.length : text.indexOf('\n', index);
            blank(masked, index, stop);
            index = stop;
        } else if (character === '/' && next === '*') {
            const stop = text.indexOf('*/', index + 2) < 0 ? text.length : text.indexOf('*/', index + 2) + 2;
            blank(masked, index, stop);
            index = stop;
        } else if (character === "'" || character === '"') {
            const end = scanQuoted(text, index);
            literals.push({ start: index, end, quote: character, value: decodeJsString(text.slice(index + 1, Math.max(index + 1, end - 1))), expressions: false });
            blank(masked, index + 1, end - 1);
            index = end;
            previous = character;
        } else if (character === '`') {
            const template = scanTemplateLiteral(text, index);
            literals.push({ start: index, end: template.end, quote: '`', value: decodeJsString(text.slice(index + 1, template.end - 1)), expressions: template.expressions });
            blank(masked, index + 1, template.end - 1);
            index = template.end;
            previous = '`';
        } else if (character === '/' && (previous === '' || '(,=:[!&|?{};+-*%<>~^'.includes(previous) || ['return', 'typeof', 'case', 'in', 'of'].includes(previousWord))) {
            const end = scanRegExp(text, index);
            blank(masked, index + 1, end);
            index = end;
            previous = '/';
            previousWord = '';
        } else {
            if (/[A-Za-z_$0-9]/.test(character)) {
                previousWord = /[A-Za-z_$0-9]/.test(previous) ? previousWord + character : character;
                previous = character;
            } else if (!/\s/.test(character)) {
                previous = character;
                previousWord = '';
            }
            index++;
        }
    }
    return { text, masked: masked.join(''), literals, line: createLineLocator(text) };
}

function depthMap(masked) {
    const depths = new Int32Array(masked.length + 1);
    let depth = 0;
    for (let index = 0; index < masked.length; index++) {
        depths[index] = depth;
        const character = masked[index];
        if (character === '(' || character === '[' || character === '{') depth++;
        else if (character === ')' || character === ']' || character === '}') depth--;
    }
    depths[masked.length] = depth;
    return depths;
}

// Property `key: value` pairs written directly inside the object literal assigned to `objectName`.
function objectProperties(prepared, objectName) {
    const { masked, literals } = prepared;
    const depths = depthMap(masked);
    let range = { start: 0, end: masked.length, depth: null };
    const declaration = new RegExp(`\\b${escapeRegExp(objectName)}\\b[^=;]*=`).exec(masked);
    if (declaration) {
        const open = masked.indexOf('{', declaration.index + declaration[0].length);
        const close = open < 0 ? -1 : matchingBracket(masked, open);
        if (close > open) range = { start: open + 1, end: close, depth: depths[open] + 1 };
    }
    const candidates = [];
    for (const literal of literals) {
        if (literal.start >= range.start && literal.end <= range.end && literal.quote !== '`') {
            candidates.push({ start: literal.start, end: literal.end, name: literal.value, quoted: true });
        }
    }
    for (const match of masked.slice(range.start, range.end).matchAll(/[A-Za-z_$][\w$]*/g)) {
        const start = range.start + match.index;
        if (/[\w$'"`]/.test(masked[start - 1] ?? '')) continue;
        candidates.push({ start, end: start + match[0].length, name: match[0], quoted: false });
    }
    const properties = [];
    for (const candidate of candidates) {
        const before = previousSignificant(masked, candidate.start);
        if (before >= 0 && masked[before] !== '{' && masked[before] !== ',') continue;
        const colon = nextSignificant(masked, candidate.end);
        if (masked[colon] !== ':') continue;
        properties.push({ ...candidate, colon, depth: depths[candidate.start] });
    }
    const depth = range.depth ?? Math.min(...properties.map(property => property.depth));
    return properties
        .filter(property => property.depth === depth)
        .sort((left, right) => left.start - right.start)
        .map(property => {
            let end = property.colon + 1;
            let level = 0;
            for (; end < masked.length; end++) {
                const character = masked[end];
                if (character === '(' || character === '[' || character === '{') level++;
                else if (character === ')' || character === ']' || character === '}') {
                    if (level === 0) break;
                    level--;
                } else if (character === ',' && level === 0) break;
            }
            return { ...property, valueStart: property.colon + 1, valueEnd: end, declared: Boolean(declaration) };
        });
}

function placeholders(text) {
    return new Set([...text.matchAll(/\{([A-Za-z_$][\w$]*)\}/g)].map(match => match[1]));
}

function stringValue(prepared, start, end) {
    const { masked, literals } = prepared;
    const range = trimRange(masked, start, end);
    if (range.start >= range.end) return null;
    const parts = [];
    let cursor = range.start;
    for (;;) {
        const literal = literals.find(candidate => candidate.start === cursor);
        if (!literal || literal.expressions) return null;
        parts.push(literal.value);
        cursor = nextSignificant(masked, literal.end);
        if (cursor >= range.end) return parts.join('');
        if (masked[cursor] !== '+') return null;
        cursor = nextSignificant(masked, cursor + 1);
    }
}

function classifyMessage(prepared, property, constants) {
    const raw = prepared.text.slice(property.valueStart, property.valueEnd).trim();
    const value = stringValue(prepared, property.valueStart, property.valueEnd);
    if (value !== null) return { kind: 'string', text: value, placeholders: placeholders(value) };
    if (IDENTIFIER.test(raw) && constants.has(raw)) {
        const text = constants.get(raw);
        return { kind: 'string', text, placeholders: placeholders(text) };
    }
    const arrow = /^(?:async\s+)?(?:\(\s*([A-Za-z_$][\w$]*)?\s*(?:\??:\s*[^)]*)?\)|([A-Za-z_$][\w$]*))\s*(?::\s*[^=]+?)?\s*=>/.exec(raw);
    const fn = /^function\s*\w*\s*\(\s*([A-Za-z_$][\w$]*)?/.exec(raw);
    const destructured = /^(?:async\s+)?\(\s*\{([^}]*)\}/.exec(raw);
    if (destructured) {
        const names = destructured[1]
            .split(',')
            .map(part => part.split(/[:=]/)[0].trim())
            .filter(name => IDENTIFIER.test(name));
        return { kind: 'function', references: new Set(names) };
    }
    const parameter = arrow ? (arrow[1] ?? arrow[2]) : fn ? fn[1] : undefined;
    if (arrow || fn) {
        if (!parameter) return { kind: 'function', references: new Set() };
        const name = escapeRegExp(parameter);
        const pattern = new RegExp(`(?<![\\w$.])${name}\\s*(?:(?:\\?\\.)?\\s*\\[\\s*(['"\`])([^'"\`]+)\\1\\s*\\]|(?:\\?\\.|\\.)\\s*([A-Za-z_$][\\w$]*))`, 'g');
        const references = new Set([...raw.matchAll(pattern)].map(match => match[2] ?? match[3]));
        return { kind: 'function', references };
    }
    return { kind: 'other' };
}

/**
 * Parses `api-error-messages.ts`: the properties of the `API_ERROR_MESSAGES` object literal (quoted or not).
 */
export function parseMessages(source, config = DEFAULT_CONFIG) {
    const prepared = prepareTypeScript(source);
    const constants = new Map();
    for (const match of prepared.masked.matchAll(/\bconst\s+([A-Za-z_$][\w$]*)\s*(?::\s*string\s*)?=\s*/g)) {
        const start = match.index + match[0].length;
        const end = prepared.masked.indexOf(';', start);
        const value = end > start ? stringValue(prepared, start, end) : null;
        if (value !== null) constants.set(match[1], value);
    }
    const entries = new Map();
    const findings = [];
    const properties = objectProperties(prepared, config.messagesObject);
    if (properties.length > 0 && !properties[0].declared) {
        findings.push({ rule: 'messages-object-missing', message: `object ${config.messagesObject} not found: reading the outermost object properties instead` });
    }
    for (const property of properties) {
        const line = prepared.line(property.start);
        if (entries.has(property.name)) {
            findings.push({ rule: 'messages-duplicate', line, message: `'${property.name}' is already defined at line ${entries.get(property.name).line}` });
            continue;
        }
        entries.set(property.name, { key: property.name, line, ...classifyMessage(prepared, property, constants) });
    }
    return { entries, findings };
}

/**
 * Parses `api-error-behaviors.ts`. Keys are collected from:
 * - the properties of the `API_ERROR_BEHAVIORS` object literal, when it is one (otherwise from every quoted property);
 * - the string values of `key`, `keys`, `pattern`, `patterns`, `errorKey`, `errorKeys`, `match`, `matches`, also in arrays;
 * - the first element of `['key', behavior]` pairs (Map entries) and `case 'key':` labels.
 */
export function parseBehaviors(source, config = DEFAULT_CONFIG) {
    const prepared = prepareTypeScript(source);
    const { masked, literals } = prepared;
    const keys = new Map();
    const add = (key, start) => {
        if (!/^[A-Za-z*][\w*]*(?:\.[\w*{}]+)*$/.test(key) || keys.has(key)) return;
        keys.set(key, { key, line: prepared.line(start) });
    };
    const objectMode = new RegExp(`\\b${escapeRegExp(config.behaviorsObject)}\\b[^=;]*=\\s*(?:Object\\.freeze\\(\\s*)?\\{`).test(masked);
    if (objectMode) {
        for (const property of objectProperties(prepared, config.behaviorsObject)) add(property.name, property.start);
    }
    const depths = depthMap(masked);
    // The opening bracket enclosing `position`.
    const opener = position => {
        const level = depths[position];
        for (let scan = position - 1; scan >= 0; scan--) {
            if ('([{'.includes(masked[scan]) && depths[scan] === level - 1) return scan;
        }
        return -1;
    };
    for (const literal of literals) {
        if (literal.quote === '`' && literal.expressions) continue;
        const before = previousSignificant(masked, literal.start);
        const after = nextSignificant(masked, literal.end);
        if (masked[after] === ':') {
            const isProperty = masked[before] === '{' || masked[before] === ',';
            if ((isProperty && !objectMode) || /\bcase$/.test(masked.slice(Math.max(0, before - 3), before + 1))) add(literal.value, literal.start);
            continue;
        }
        let cursor = before;
        if (masked[before] === '[' && masked[after] === ',') {
            const outer = previousSignificant(masked, before);
            if (masked[outer] === '[' || masked[outer] === ',') {
                add(literal.value, literal.start);
                continue;
            }
        }
        if (masked[before] === '[' || masked[before] === ',') {
            const open = opener(literal.start);
            if (open >= 0 && masked[open] === '[') cursor = previousSignificant(masked, open);
        }
        if (masked[cursor] !== ':') continue;
        const ownerEnd = previousSignificant(masked, cursor);
        const owner = /([A-Za-z_$][\w$]*)['"]?$/.exec(prepared.text.slice(Math.max(0, ownerEnd - 40), ownerEnd + 1));
        if (owner && KEY_PROPERTIES.has(owner[1])) add(literal.value, literal.start);
    }
    return { keys, findings: [] };
}

// ---------------------------------------------------------------------------------------------------------------------
// Comparison

function locationText(file, line) {
    if (!file) return '';
    return line ? `${file}:${line}` : file;
}

function locationList(sites, limit = 3) {
    const unique = [...new Set(sites.map(site => locationText(site.file, site.line)))];
    const shown = unique.slice(0, limit).join(', ');
    return unique.length > limit ? `${shown} (+${unique.length - limit} more)` : shown;
}

function sameParameters(expected, actual) {
    const all = new Set(expected.map(parameter => parameter.name));
    const required = expected.filter(parameter => !parameter.optional).map(parameter => parameter.name);
    const names = new Set(actual);
    return {
        missing: required.filter(name => !names.has(name)),
        unexpected: [...names].filter(name => !all.has(name)),
    };
}

function describeParameters(expected) {
    if (expected.length === 0) return 'none';
    return expected.map(parameter => (parameter.optional ? `${parameter.name}?` : parameter.name)).join(', ');
}

function lookupCatalog(catalog, key) {
    const exact = catalog.keys.get(key);
    if (exact) return exact;
    for (const entry of catalog.keys.values()) if (entry.pattern?.test(key)) return entry;
    return null;
}

/**
 * Compares backend, catalog and frontend. `messages` and `behaviors` are null when their file is missing.
 */
export function compareErrorCatalog({ backend, catalog, messages, behaviors, config = DEFAULT_CONFIG }) {
    const errors = [];
    const warnings = [];
    const genericKeys = new Set(config.genericKeys);
    const error = (rule, message, location = '') => errors.push({ rule, message, location });
    const warning = (rule, message, location = '') => warnings.push({ rule, message, location });

    for (const finding of backend.findings) {
        const target = finding.rule === 'template-code-dynamic' ? warning : error;
        target(finding.rule, finding.message, locationText(finding.file, finding.line));
    }
    for (const finding of catalog.findings) error(finding.rule, finding.message, locationText(CATALOG_PATH, finding.line));
    for (const finding of messages?.findings ?? []) {
        const target = finding.rule === 'messages-object-missing' ? warning : error;
        target(finding.rule, finding.message, locationText(MESSAGES_PATH, finding.line));
    }

    // Backend keys: literal throw sites, enum families and generic keys.
    const backendKeys = new Map();
    const addBackend = (key, site) => {
        if (!backendKeys.has(key)) backendKeys.set(key, []);
        backendKeys.get(key).push(site);
    };
    const dynamicSites = [];
    for (const site of backend.sites) {
        if (site.kind === 'literal') addBackend(site.key, site);
        else if (site.kind === 'dynamic') dynamicSites.push(site);
    }
    for (const familyKey of backend.familyKeys) addBackend(familyKey.key, { ...familyKey, kind: 'family', names: [], unresolved: [] });
    for (const key of genericKeys) if (!backendKeys.has(key)) backendKeys.set(key, [{ kind: 'generic', names: [], unresolved: [] }]);

    for (const site of dynamicSites) {
        warning('backend-key-dynamic', `key expression \`${site.text}\` cannot be resolved statically${site.via.length ? ` (via ${site.via.join(' → ')})` : ''}`, locationText(site.file, site.line));
    }

    // 1. Backend key missing from the catalog.
    for (const [key, sites] of [...backendKeys].sort(([left], [right]) => left.localeCompare(right))) {
        if (sites[0].kind === 'generic') continue;
        if (!lookupCatalog(catalog, key)) error('backend-key-not-in-catalog', `\`${key}\` is thrown by the backend but missing from the catalog`, locationList(sites));
    }

    // 2. Catalog key no longer thrown by the backend.
    const missingFamilies = backend.families.filter(family => family.missing);
    for (const [key, entry] of catalog.keys) {
        if (genericKeys.has(key)) continue;
        const thrown = entry.pattern ? [...backendKeys.keys()].some(candidate => entry.pattern.test(candidate)) : backendKeys.has(key);
        if (thrown) continue;
        const family = missingFamilies.find(candidate => candidate.keys.some(template => catalogKeyPattern(template).test(key)));
        const hint = family ? ` (it may come from the missing enum ${family.enumName})` : '';
        error('catalog-key-not-thrown', `\`${key}\` is in the catalog but not thrown by the backend${hint}`, locationText(CATALOG_PATH, entry.line));
    }

    // 3. Template issue codes, in both directions.
    const templateCodes = new Map();
    for (const site of backend.templateSites) {
        if (!templateCodes.has(site.code)) templateCodes.set(site.code, []);
        templateCodes.get(site.code).push(site);
    }
    for (const [code, sites] of [...templateCodes].sort(([left], [right]) => left.localeCompare(right))) {
        if (!catalog.issueCodes.has(code)) error('template-code-not-in-catalog', `template issue code \`${code}\` is produced by the validator but missing from the catalog`, locationList(sites));
    }
    for (const [code, entry] of catalog.issueCodes) {
        if (!templateCodes.has(code)) error('catalog-code-not-produced', `template issue code \`${code}\` is in the catalog but not produced by the validator`, locationText(CATALOG_PATH, entry.line));
    }

    // 4. Backend parameters (`.with("name", …)`) against the catalog «Parametri».
    const checkSiteParameters = (label, entry, site) => {
        if (site.unresolved.length > 0) {
            warning('backend-parameters-unresolved', `parameters of ${label} not checked: ${[...new Set(site.unresolved)].join('; ')}`, locationText(site.file, site.line));
            return;
        }
        const { missing, unexpected } = sameParameters(entry.parameters, site.names);
        if (missing.length || unexpected.length) {
            const details = [missing.length ? `missing ${missing.join(', ')}` : '', unexpected.length ? `unexpected ${unexpected.join(', ')}` : ''].filter(Boolean).join('; ');
            error('backend-parameters', `${label} is thrown with parameters {${[...new Set(site.names)].join(', ')}} but the catalog lists {${describeParameters(entry.parameters)}}: ${details}`, locationText(site.file, site.line));
        }
    };
    for (const site of backend.sites) {
        if (site.kind === 'literal') {
            const entry = lookupCatalog(catalog, site.key);
            if (entry) checkSiteParameters(`\`${site.key}\``, entry, site);
        } else if (site.kind === 'family') {
            if (site.unresolved.length > 0) {
                warning('backend-parameters-unresolved', `parameters of the ${site.template} keys not checked: ${[...new Set(site.unresolved)].join('; ')}`, locationText(site.file, site.line));
                continue;
            }
            for (const familyKey of backend.familyKeys.filter(candidate => candidate.family === site.family.enumName && catalogKeyPattern(site.template)?.test(candidate.key))) {
                const entry = lookupCatalog(catalog, familyKey.key);
                if (entry) checkSiteParameters(`\`${familyKey.key}\` (${site.template})`, entry, site);
            }
        }
    }
    for (const site of backend.templateSites) {
        const entry = catalog.issueCodes.get(site.code);
        if (entry) checkSiteParameters(`template issue \`${site.code}\``, entry, site);
    }

    // 5. Frontend messages.
    if (messages) {
        const has = key => messages.entries.has(key);
        for (const [key, sites] of [...backendKeys].sort(([left], [right]) => left.localeCompare(right))) {
            const entry = lookupCatalog(catalog, key);
            if (entry?.messageOptional || entry?.pattern) continue;
            if (!has(key)) {
                const where = sites[0].kind === 'generic' ? 'generic key' : locationList(sites);
                error('frontend-message-missing', `\`${key}\` is thrown by the backend but has no message in ${MESSAGES_PATH}`, where);
            }
        }
        for (const [code, sites] of [...templateCodes].sort(([left], [right]) => left.localeCompare(right))) {
            const key = `${config.templateIssuePrefix}${code}`;
            if (catalog.issueCodes.get(code)?.messageOptional) continue;
            if (!has(key)) error('frontend-message-missing', `\`${key}\` (template issue produced by the validator) has no message in ${MESSAGES_PATH}`, locationList(sites));
        }
        for (const [key, message] of messages.entries) {
            const location = locationText(MESSAGES_PATH, message.line);
            if (CONSTRAINT_CODE.test(key)) continue;
            let entry;
            if (key.startsWith(config.templateIssuePrefix)) {
                entry = catalog.issueCodes.get(key.slice(config.templateIssuePrefix.length));
                if (!entry) {
                    error('frontend-message-unknown', `'${key}' is not a template issue code of the catalog`, location);
                    continue;
                }
            } else {
                entry = lookupCatalog(catalog, key);
                if (!entry) {
                    error('frontend-message-unknown', `'${key}' is not in the catalog`, location);
                    continue;
                }
            }
            if (message.kind === 'string') {
                const { missing, unexpected } = sameParameters(entry.parameters, message.placeholders);
                if (missing.length || unexpected.length) {
                    const details = [missing.length ? `missing {${missing.join('}, {')}}` : '', unexpected.length ? `unexpected {${unexpected.join('}, {')}}` : ''].filter(Boolean).join('; ');
                    error('frontend-message-parameters', `'${key}' uses placeholders {${[...message.placeholders].join(', ')}} but the catalog lists {${describeParameters(entry.parameters)}}: ${details}`, location);
                }
            } else if (message.kind === 'function') {
                const all = new Set(entry.parameters.map(parameter => parameter.name));
                const unexpected = [...message.references].filter(name => !all.has(name));
                if (unexpected.length) {
                    error('frontend-message-parameters', `'${key}' reads parameters {${unexpected.join(', ')}} that the catalog does not list (catalog: {${describeParameters(entry.parameters)}})`, location);
                }
            }
        }
    }

    // 6. Frontend behaviors.
    if (behaviors) {
        const catalogSamples = [...catalog.keys.keys()].map(key => key.replace(/\{[^}]+\}/g, '0'));
        for (const [key, behavior] of behaviors.keys) {
            const location = locationText(BEHAVIORS_PATH, behavior.line);
            const matched = key.includes('*') ? catalogSamples.some(sample => behaviorPattern(key).test(sample)) : Boolean(lookupCatalog(catalog, key));
            if (!matched) error('behavior-key-unknown', `'${key}' matches no catalog key`, location);
        }
    }

    const order = (left, right) => left.rule.localeCompare(right.rule) || left.location.localeCompare(right.location, 'en', { numeric: true }) || left.message.localeCompare(right.message);
    errors.sort(order);
    warnings.sort(order);

    const literalSites = backend.sites.filter(site => site.kind === 'literal');
    return {
        errors,
        warnings,
        summary: {
            javaFiles: backend.files,
            backendKeys: new Set(literalSites.map(site => site.key)).size,
            throwSites: literalSites.length,
            familyKeys: new Set(backend.familyKeys.map(familyKey => familyKey.key)).size,
            dynamicSites: dynamicSites.length,
            templateCodes: templateCodes.size,
            catalogKeys: catalog.keys.size,
            catalogIssueCodes: catalog.issueCodes.size,
            messages: messages ? messages.entries.size : null,
            behaviors: behaviors ? behaviors.keys.size : null,
        },
    };
}

// ---------------------------------------------------------------------------------------------------------------------
// Repository

function listJavaFiles(repositoryRoot) {
    const files = new Map();
    const root = path.resolve(repositoryRoot, JAVA_ROOT);
    const walk = directory => {
        for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
            const absolute = path.join(directory, entry.name);
            if (entry.isDirectory()) walk(absolute);
            else if (entry.isFile() && entry.name.endsWith('.java')) {
                files.set(path.relative(repositoryRoot, absolute).split(path.sep).join('/'), fs.readFileSync(absolute, 'utf8'));
            }
        }
    };
    if (fs.existsSync(root)) walk(root);
    return files;
}

function readOptional(repositoryRoot, repositoryPath) {
    try {
        return fs.readFileSync(path.resolve(repositoryRoot, ...repositoryPath.split('/')), 'utf8');
    } catch {
        return null;
    }
}

export function checkRepository(repositoryRoot = defaultRepositoryRoot, config = DEFAULT_CONFIG) {
    const backend = extractBackend(listJavaFiles(repositoryRoot), config);
    const markdown = readOptional(repositoryRoot, CATALOG_PATH);
    const catalog = markdown === null ? { keys: new Map(), issueCodes: new Map(), findings: [{ rule: 'file-missing', message: 'catalog not found' }] } : parseCatalog(markdown, config);
    const messagesSource = readOptional(repositoryRoot, MESSAGES_PATH);
    const behaviorsSource = readOptional(repositoryRoot, BEHAVIORS_PATH);
    const result = compareErrorCatalog({
        backend,
        catalog,
        messages: messagesSource === null ? null : parseMessages(messagesSource, config),
        behaviors: behaviorsSource === null ? null : parseBehaviors(behaviorsSource, config),
        config,
    });
    if (messagesSource === null) result.errors.unshift({ rule: 'file-missing', message: 'frontend message catalog not found: messages and their parameters are not checked', location: MESSAGES_PATH });
    if (behaviorsSource === null) result.errors.unshift({ rule: 'file-missing', message: 'frontend behavior registry not found: behavior keys are not checked', location: BEHAVIORS_PATH });
    return result;
}

export function formatReport(result) {
    const { summary } = result;
    const lines = [
        'Error catalog check',
        `  backend:   ${summary.backendKeys} literal keys from ${summary.throwSites} throw sites in ${summary.javaFiles} Java files, ${summary.familyKeys} enum family keys, ${summary.dynamicSites} dynamic sites, ${summary.templateCodes} template issue codes`,
        `  catalog:   ${summary.catalogKeys} keys, ${summary.catalogIssueCodes} template issue codes (${CATALOG_PATH})`,
        `  messages:  ${summary.messages === null ? 'file missing' : `${summary.messages} entries`} (${MESSAGES_PATH})`,
        `  behaviors: ${summary.behaviors === null ? 'file missing' : `${summary.behaviors} keys`} (${BEHAVIORS_PATH})`,
    ];
    for (const [label, findings] of [
        ['ERROR', result.errors],
        ['WARNING', result.warnings],
    ]) {
        let rule = null;
        for (const finding of findings) {
            if (finding.rule !== rule) {
                rule = finding.rule;
                const count = findings.filter(candidate => candidate.rule === rule).length;
                lines.push('', `${label} ${rule} (${count})`);
            }
            lines.push(`  - ${finding.message}${finding.location ? `  [${finding.location}]` : ''}`);
        }
    }
    lines.push('', `Result: ${result.errors.length} error(s), ${result.warnings.length} warning(s)`);
    return lines.join('\n');
}
