import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { Injectable, isDevMode } from '@angular/core';
import { catchError, from, map, Observable, of } from 'rxjs';
import {
    API_ERROR_DEFAULT_SUMMARY,
    API_ERROR_NETWORK_TEXT,
    API_ERROR_NOT_FOUND_SUMMARY,
    API_ERROR_VERSION_CONFLICT_SUMMARY,
    ApiErrorBehavior,
    apiErrorBehavior,
    apiErrorGenericText,
    apiErrorMessage,
    apiFieldErrorMessage,
    loanDurationIssueMessage,
    loanTemplateIssueMessage
} from '../constants';
import { ApiErrorArgs, ApiErrorBody, ApiErrorIssue, ApiFieldError } from '../module';
import { ERROR_SUMMARY, HANDLED_ERRORS, matchesApiErrorKey } from './api-error-context';

/** An error response translated for the user. */
export interface ResolvedApiError {
    /** Catalog key without the `error.` prefix; `http.{status}` when the response carries none. */
    key: string;
    status: number;
    args: ApiErrorArgs;
    fieldErrors: ApiFieldError[];
    /** Reference of the error in the backend log. */
    errorId?: string;
    /** Toast title. */
    summary: string;
    /** Toast detail, or the text shown under the field by a component that handles the key. */
    detail: string;
    behavior?: ApiErrorBehavior;
    /** The caller declared the key in `HANDLED_ERRORS`: it shows the error itself, the interceptor shows no toast. */
    handled: boolean;
}

/** The part of the request that drives the resolution: the declared context and the method. */
export type ApiErrorRequest = Pick<HttpRequest<unknown>, 'context'>;

const KEY_PREFIX = 'error.';
const HTTP_KEY_PREFIX = 'http.';

/**
 * The only place where an error response becomes a text. The backend sends a key, its parameters and an
 * `errorId`; the texts come from the message catalog. The `title` of the response is never read.
 */
@Injectable({ providedIn: 'root' })
export class ApiErrorService {
    /** Translates an error response, with the toast title and the handled keys declared by the request. */
    resolve(error: HttpErrorResponse, request?: ApiErrorRequest): ResolvedApiError {
        const status = error.status;
        const body = this.body(error.error);
        const key = this.keyOf(status, body);
        const args = body.args ?? {};
        const behavior = apiErrorBehavior(key);
        const declaredSummary = request?.context.get(ERROR_SUMMARY);
        const resolved = {
            key,
            status,
            args,
            fieldErrors: body.fieldErrors ?? [],
            errorId: body.errorId,
            behavior,
            handled: matchesApiErrorKey(key, request?.context.get(HANDLED_ERRORS) ?? [])
        };

        if (status === 0) return { ...resolved, ...API_ERROR_NETWORK_TEXT };

        const httpKey = key.startsWith(HTTP_KEY_PREFIX);
        const message = httpKey ? undefined : apiErrorMessage(key, args);
        if (message === undefined && !httpKey && isDevMode()) console.warn(`Error key "${key}" is missing from api-error-messages.ts`);

        const generic = apiErrorGenericText(status);
        const detail = message ?? generic.detail;
        const withErrorId = !!body.errorId && (status >= 500 || (message === undefined && !httpKey));
        const fallbackSummary = message === undefined ? generic.summary : this.defaultSummary(key);
        return {
            ...resolved,
            summary: behavior === 'versionConflict' ? API_ERROR_VERSION_CONFLICT_SUMMARY : (declaredSummary ?? fallbackSummary),
            detail: withErrorId ? `${detail}. Codice errore: ${body.errorId}` : detail
        };
    }

    /** The key of an error response, without the `error.` prefix. */
    key(error: HttpErrorResponse): string {
        return this.keyOf(error.status, this.body(error.error));
    }

    /** The text of an error, for a component that shows it next to the field. */
    message(error: HttpErrorResponse): string {
        return this.resolve(error).detail;
    }

    /** The text of an error whose key matches one of the keys or `prefix.*` patterns the component handles; `undefined` otherwise. */
    handledMessage(error: HttpErrorResponse, keys: readonly string[]): string | undefined {
        const resolved = this.resolve(error);
        return matchesApiErrorKey(resolved.key, keys) ? resolved.detail : undefined;
    }

    /** The text of a field rejected by `@Valid`. */
    fieldErrorMessage(fieldError: ApiFieldError): string {
        return apiFieldErrorMessage(fieldError);
    }

    /** The text of a duration problem in the loan sheet proposal (`inventory.loanDuration.{code}`). */
    loanSheetIssueMessage(issue: ApiErrorIssue): string {
        return loanDurationIssueMessage(issue);
    }

    /** The text of an outcome of the loan template check (`inventory.loanTemplate.issue.{code}`). */
    loanTemplateIssueMessage(issue: ApiErrorIssue): string {
        return loanTemplateIssueMessage(issue);
    }

    /**
     * Reads the JSON body of an error received as a Blob (requests with `responseType: 'blob'`, such as PDF previews),
     * so that its key can be resolved. Any other error is returned unchanged.
     */
    readBody(error: HttpErrorResponse): Observable<HttpErrorResponse> {
        const body: unknown = error.error;
        if (!(body instanceof Blob)) return of(error);
        return from(body.text()).pipe(
            map((text) => new HttpErrorResponse({ error: this.parse(text) ?? text, headers: error.headers, status: error.status, statusText: error.statusText, url: error.url ?? undefined })),
            catchError(() => of(error))
        );
    }

    private keyOf(status: number, body: ApiErrorBody): string {
        if (status === 0) return `${HTTP_KEY_PREFIX}0`;
        const message = body.message;
        if (message?.startsWith(KEY_PREFIX) && message.length > KEY_PREFIX.length) return message.slice(KEY_PREFIX.length);
        // A 401 is always an authentication failure, even when an intermediary strips the body.
        return status === 401 ? 'auth.unauthenticated' : `${HTTP_KEY_PREFIX}${status}`;
    }

    private defaultSummary(key: string): string {
        return key.endsWith('.notFound') ? API_ERROR_NOT_FOUND_SUMMARY : API_ERROR_DEFAULT_SUMMARY;
    }

    /** The fields of the contract; `title` and anything else written by the backend are ignored. */
    private body(raw: unknown): ApiErrorBody {
        const value = typeof raw === 'string' ? this.parse(raw) : raw;
        if (!this.isRecord(value)) return {};
        const fieldErrors = Array.isArray(value['fieldErrors']) ? value['fieldErrors'].filter((item): item is ApiFieldError => this.isRecord(item) && typeof item['code'] === 'string') : undefined;
        return {
            status: typeof value['status'] === 'number' ? value['status'] : undefined,
            message: typeof value['message'] === 'string' ? value['message'] : undefined,
            args: this.isRecord(value['args']) ? (value['args'] as ApiErrorArgs) : undefined,
            fieldErrors,
            errorId: typeof value['errorId'] === 'string' ? value['errorId'] : undefined
        };
    }

    private parse(text: string): unknown {
        try {
            return JSON.parse(text);
        } catch {
            return undefined;
        }
    }

    private isRecord(value: unknown): value is Record<string, unknown> {
        return value !== null && typeof value === 'object' && !Array.isArray(value);
    }
}
