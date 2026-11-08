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

/** Le tavolozze con cui Aura e Lara colorano le severità di p-message e p-toast. */
export const SEVERITY_PALETTES = { info: 'blue', success: 'green', warn: 'yellow', error: 'red' } as const;

export type MessageSeverity = keyof typeof SEVERITY_PALETTES;

/** Le tinte del testo delle severità in modalità chiara e in modalità scura. */
const LIGHT_SHADES: Record<MessageSeverity, number> = { info: 700, success: 700, warn: 800, error: 700 };
const DARK_SHADES: Record<MessageSeverity, number> = { info: 400, success: 400, warn: 400, error: 400 };

/** Il token di ogni severità, con il testo alla tinta che `shades` le assegna nella sua tavolozza. */
function bySeverity<T>(shades: Record<MessageSeverity, number>, token: (color: string) => T): Record<MessageSeverity, T> {
    const entries = (Object.keys(SEVERITY_PALETTES) as MessageSeverity[]).map((severity) => [severity, token(`{${SEVERITY_PALETTES[severity]}.${shades[severity]}}`)]);
    return Object.fromEntries(entries) as Record<MessageSeverity, T>;
}

/**
 * Scostamenti di p-message. In modalità chiara Aura e Lara scrivono il testo con la tinta 600 della
 * tavolozza su un fondo alla tinta 50: warn si ferma a 2,84:1, success a 3,16:1 ed error a 4,45:1.
 * La tinta 700 basta a info, success ed error, che arrivano almeno a 4,58:1. Il giallo è la
 * tavolozza più chiara: alla tinta 700 le varianti «outlined» e «simple», che scrivono sul fondo
 * della pagina (surface.100), si fermano a 4,49:1, e warn scende alla tinta 800. In modalità scura il
 * testo è alla tinta 500 sopra la stessa tinta al 16%: sul fondo delle card (surface.900) info ed
 * error si fermano a 3,96:1 e 3,99:1, mentre la tinta 400 arriva almeno a 5,43:1 per tutte.
 * accessible-theme.spec.ts misura questi rapporti sui token del preset installato.
 */
export const ACCESSIBLE_MESSAGE = {
    colorScheme: {
        light: bySeverity(LIGHT_SHADES, (color) => ({ color, outlined: { color }, simple: { color } })),
        dark: bySeverity(DARK_SHADES, (color) => ({ color, outlined: { color }, simple: { color } }))
    }
};

/** Scostamenti di p-toast, che usa per il titolo le stesse tinte e gli stessi fondi di p-message. */
export const ACCESSIBLE_TOAST = {
    colorScheme: {
        light: bySeverity(LIGHT_SHADES, (color) => ({ color })),
        dark: bySeverity(DARK_SHADES, (color) => ({ color }))
    }
};

export type AccessibleComponents = {
    button: typeof ACCESSIBLE_BUTTON;
    message?: typeof ACCESSIBLE_MESSAGE;
    toast?: typeof ACCESSIBLE_TOAST;
};

/**
 * Gli scostamenti dei componenti per il preset scelto. Nora dà a messaggi e toast un fondo pieno con
 * il testo chiaro, e le tinte scure qui sopra lo renderebbero illeggibile: tiene i propri.
 */
export function accessibleComponents(preset: string | undefined): AccessibleComponents {
    if (preset === 'Nora') return { button: ACCESSIBLE_BUTTON };
    return { button: ACCESSIBLE_BUTTON, message: ACCESSIBLE_MESSAGE, toast: ACCESSIBLE_TOAST };
}
