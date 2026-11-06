import { PUBLIC_SIGNATURE_PATH } from './signature-labels';

/**
 * Pagine dell'applicativo aperte senza login: l'applicativo non avvia Keycloak e non legge i dati del tenant.
 * `exact` vale per il percorso intero, `prefixes` per quelli che proseguono, come la versione di un documento.
 */
export const PUBLIC_PAGES = {
    exact: ['/legal/terms', '/legal/privacy'],
    prefixes: ['/legal/terms/', '/legal/privacy/', PUBLIC_SIGNATURE_PATH]
} as const;

/** Se la pagina aperta è pubblica: i documenti legali pubblicati e la pagina di firma del genitore. */
export const publicPage = (path: string = globalThis.location?.pathname ?? ''): boolean => PUBLIC_PAGES.exact.some((page) => path === page) || PUBLIC_PAGES.prefixes.some((prefix) => path.startsWith(prefix));
