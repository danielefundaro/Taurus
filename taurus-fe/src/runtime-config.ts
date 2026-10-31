import { environment } from './environments/environment';

/**
 * Configurazione letta all'avvio da `assets/config.json`, prima che l'applicazione parta.
 *
 * Gli indirizzi dell'API e di Keycloak non possono essere fissati nel bundle: il file compilato
 * viene costruito una volta e servito a browser che stanno fuori dalla rete del deploy, dove
 * `http://backend:8080` non esiste. Il container scrive questo file dalle variabili d'ambiente
 * dell'host (`docker-entrypoint.sh`), quindi la stessa immagine serve ambienti diversi senza
 * ricostruirla.
 *
 * Quello che il file non dichiara resta al valore compilato in `environment`: in sviluppo non serve
 * nessun file, e un deploy che dimentica una chiave usa il valore predefinito invece di partire a
 * metà.
 */
export interface RuntimeConfig {
    baseUrl?: string;
    vapidPublicKey?: string;
    keycloak?: {
        baseurl?: string;
        realm?: string;
        clientId?: string;
    };
}

const CONFIG_URL = 'assets/config.json';

/** Sovrascrive `environment` con i valori serviti, se ci sono. Non solleva mai: il bundle ha i suoi. */
export const loadRuntimeConfig = async (): Promise<void> => {
    const config = await read();
    if (config) {
        if (config.baseUrl) environment.baseUrl = config.baseUrl;
        if (config.vapidPublicKey) environment.vapidPublicKey = config.vapidPublicKey;
        if (config.keycloak?.baseurl) environment.keycloak.baseurl = config.keycloak.baseurl;
        if (config.keycloak?.realm) environment.keycloak.realm = config.keycloak.realm;
        if (config.keycloak?.clientId) environment.keycloak.clientId = config.keycloak.clientId;
    }

    // Keycloak costruisce gli indirizzi dei suoi endpoint concatenando `url` e li usa anche nelle
    // redirezioni del browser: un percorso relativo va reso assoluto sull'origine corrente.
    environment.keycloak.baseurl = absolute(environment.keycloak.baseurl);
};

const absolute = (url: string): string => (url.startsWith('/') ? new URL(url, window.location.origin).toString().replace(/\/$/, '') : url);

const read = async (): Promise<RuntimeConfig | null> => {
    try {
        // `no-store`: il file cambia a ogni deploy e deve valere subito, anche con il bundle in cache.
        const response = await fetch(CONFIG_URL, { cache: 'no-store' });
        if (!response.ok) {
            console.warn(`Configurazione a runtime non disponibile (HTTP ${response.status}): valgono i valori compilati.`);
            return null;
        }

        const config = (await response.json()) as RuntimeConfig;
        return config && typeof config === 'object' ? config : null;
    } catch (error) {
        // Un file assente o non leggibile non deve impedire l'avvio: senza fallback l'applicazione
        // resterebbe bianca anche quando i valori compilati basterebbero.
        console.warn('Configurazione a runtime non leggibile: valgono i valori compilati.', error);
        return null;
    }
};
