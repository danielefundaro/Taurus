/**
 * A parameter of an application error: ISO date (yyyy-MM-dd), integer, string, boolean,
 * list of values or list of objects with `code` and `args`.
 */
export type ApiErrorArgValue = string | number | boolean | null | undefined | ApiErrorArgValue[] | ApiErrorArgs;

/** Parameters of an application error, by name, as listed in the error catalog. */
export interface ApiErrorArgs {
    [name: string]: ApiErrorArgValue;
}

/** A field rejected by `@Valid`: the constraint code (`NotNull`, `Size`...) with its attributes. */
export interface ApiFieldError {
    field: string;
    code: string;
    args?: ApiErrorArgs;
}

/**
 * The fields of an error response the frontend relies on. The response also carries `type` and `title`,
 * the HTTP reason phrase, which are deliberately left out: no text of the backend is ever shown.
 */
export interface ApiErrorBody {
    status?: number;
    /** `error.` followed by the catalog key. */
    message?: string;
    args?: ApiErrorArgs;
    fieldErrors?: ApiFieldError[];
    /** Opaque reference of the error, also written in the backend log line. */
    errorId?: string;
}

/** A problem returned as data (template check, sheet proposal): a catalog code with its parameters. */
export interface ApiErrorIssue {
    code: string;
    args?: ApiErrorArgs;
}
