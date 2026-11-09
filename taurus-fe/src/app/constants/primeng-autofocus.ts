import { AutoFocus } from 'primeng/autofocus';

const NEUTRALIZED = Symbol('taurus.autofocusNeutralized');

type PatchedPrototype = AutoFocus & { [NEUTRALIZED]?: true };

/**
 * Riporta l'AutoFocus di PrimeNG 21 al comportamento della 19: senza un valore esplicito il campo non
 * prende il focus.
 *
 * <p>Nella 21 la direttiva scrive l'attributo nativo `autofocus` per ogni valore diverso da `false`, e i
 * componenti le passano il proprio `autofocus`, che di norma è `undefined`. Pulsanti, select, datepicker
 * e gli altri quindici componenti che la usano escono così con `autofocus="true"`, e al primo caricamento
 * il browser dà il focus al primo di loro senza che l'utente l'abbia chiesto. La 21.1.10 è l'ultima della
 * linea, quindi la correzione sta qui: un valore assente vale `false`, mentre `true` e l'attributo vuoto
 * continuano a funzionare. Va tolta con l'aggiornamento di PrimeNG, se la versione nuova ha corretto la
 * direttiva.
 */
export function neutralizePrimeNgAutoFocusDefault(): void {
    const prototype = AutoFocus.prototype as PatchedPrototype;
    if (prototype[NEUTRALIZED]) return;

    const original = prototype.onAfterContentChecked;
    prototype.onAfterContentChecked = function (this: AutoFocus): void {
        if (this.autofocus === undefined || this.autofocus === null) {
            this.autofocus = false;
        }
        original.call(this);
    };
    prototype[NEUTRALIZED] = true;
}
