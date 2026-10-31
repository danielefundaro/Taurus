// Valori predefiniti della produzione: sono quelli di un deploy a una sola origine pubblica, dove il
// reverse proxy porta /api al backend e /auth a Keycloak. Non contengono nomi della rete interna,
// che un browser esterno non risolverebbe.
//
// Il container li sovrascrive all'avvio scrivendo `assets/config.json` dalle variabili d'ambiente
// (vedi `docker-entrypoint.sh` e `src/runtime-config.ts`): per servire l'API o Keycloak su un'altra
// origine basta configurarle, senza ricostruire l'immagine.
export const environment = {
    production: true,
    baseUrl: '/api',
    // La chiave pubblica VAPID appartiene all'installazione e deve arrivare dalla configurazione:
    // finché vale CHANGE_ME le notifiche push restano disattivate invece di fallire alla sottoscrizione.
    vapidPublicKey: 'CHANGE_ME',
    keycloak: {
        baseurl: '/auth',
        realm: 'taurus',
        clientId: 'web_app'
    }
};
