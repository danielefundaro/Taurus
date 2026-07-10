export const environment = {
    production: true,
    baseUrl: "http://backend:8080/api",
    vapidPublicKey: 'BHEmW3uZIG2dS1KLCMqxvEnuA8lk_Tkf_gzVR9Qe7B5gsSRbrPKD903tCBiGCrVqVzfTG-zuiN6MnpN50sbAYfM',
    keycloak: {
        baseurl: "http://keycloak:8081",
        realm: "taurus",
        clientId: "web-app",
    }
};