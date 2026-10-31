/**
 * Scostamenti dal preset PrimeNG per il contrasto del testo in modalità chiara.
 *
 * Aura usa la tinta 500 della tavolozza come colore primario: con il testo bianco sopra, e come
 * testo su fondo bianco, si ferma a 2,53:1, sotto il 4,5:1 che WCAG 2.1 AA chiede per il testo
 * normale. La tinta 700 arriva almeno a 4,9:1 per tutte le tavolozze offerte dal configuratore,
 * e le tinte 800 e 900 restano più scure per il passaggio del puntatore e per l'attivazione.
 * Il testo attenuato passa da surface.500 a surface.600, perché sul fondo delle pagine
 * (surface.100) la tinta 500 si ferma a 4,34:1.
 *
 * La modalità scura non è toccata: lì il testo chiaro sta su fondo scuro e i rapporti sono già
 * sopra la soglia. Il gate di accessibilità della suite end to end misura questi rapporti.
 */
export const ACCESSIBLE_LIGHT_SCHEME = {
    primary: {
        color: '{primary.700}',
        contrastColor: '#ffffff',
        hoverColor: '{primary.800}',
        activeColor: '{primary.900}'
    },
    text: {
        mutedColor: '{surface.600}',
        hoverMutedColor: '{surface.700}'
    }
};

/**
 * Scostamenti dei pulsanti, per lo stesso motivo. Le varianti «outlined» e «text» scrivono
 * l'etichetta direttamente sul fondo della pagina con la tinta 500 della propria tavolozza, che si
 * ferma fra 2,3:1 e 4,34:1; la tinta 700 passa per tutte, e «secondary» usa surface.600, la stessa
 * che il pulsante pieno adotta già.
 *
 * Le severità piene (success, info, warn, help, danger) restano come le dà Aura, con il fondo alla
 * tinta 500 e l'etichetta bianca: sono fra 2,3:1 e 4:1, ma scurirle cambierebbe il colore di quei
 * pulsanti in tutta l'applicazione. Il difetto è annotato fra i gap del runbook di test.
 */
export const ACCESSIBLE_BUTTON = {
    colorScheme: {
        light: {
            outlined: {
                secondary: { color: '{surface.600}' },
                success: { color: '{green.700}' },
                info: { color: '{sky.700}' },
                warn: { color: '{orange.700}' },
                help: { color: '{purple.700}' },
                danger: { color: '{red.700}' }
            },
            text: {
                secondary: { color: '{surface.600}' },
                success: { color: '{green.700}' },
                info: { color: '{sky.700}' },
                warn: { color: '{orange.700}' },
                help: { color: '{purple.700}' },
                danger: { color: '{red.700}' }
            }
        }
    }
};
