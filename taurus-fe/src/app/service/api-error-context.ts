import { HttpContext, HttpContextToken } from '@angular/common/http';

/** Title of the error toast of a request: it states the outcome, for example «Assegnazione non riuscita». */
export const ERROR_SUMMARY = new HttpContextToken<string | undefined>(() => undefined);

/**
 * Error keys the caller shows by itself, for example under a field: the interceptor shows no toast for them.
 * A pattern ending with `*` matches every key with that prefix, for example `inventory.loanDuration.*`.
 */
export const HANDLED_ERRORS = new HttpContextToken<readonly string[]>(() => []);

/** Declares the title of the error toast of a request. */
export const withErrorSummary = (summary: string, context: HttpContext = new HttpContext()): HttpContext => context.set(ERROR_SUMMARY, summary);

/** Declares the error keys, or `prefix.*` patterns, that the caller shows by itself. */
export const withHandledErrors = (keys: readonly string[], context: HttpContext = new HttpContext()): HttpContext => context.set(HANDLED_ERRORS, keys);

/** Whether an error key matches one of the keys or `prefix.*` patterns. */
export const matchesApiErrorKey = (key: string, patterns: readonly string[]): boolean => patterns.some((pattern) => (pattern.endsWith('*') ? key.startsWith(pattern.slice(0, -1)) : key === pattern));
