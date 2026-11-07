import { AfterViewInit, Directive, ElementRef, inject, Input, OnChanges, OnDestroy } from '@angular/core';

/** L'elemento di un ToggleButton di PrimeNG: `p-toggleButton` diventa `p-togglebutton` nel DOM. */
const TOGGLE_BUTTON = 'p-togglebutton, p-toggle-button';

/**
 * Restituisce a ToggleButton la semantica di pulsante. Da PrimeNG 19.1 il componente non disegna più un
 * `<button>`: tiene `aria-pressed`, `tabindex` e la tastiera sull'elemento host, che però non ha ruolo, così i
 * lettori di schermo non lo annunciano come pulsante e axe segnala `aria-allowed-attr`. La direttiva aggiunge
 * `role="button"` e `aria-disabled`, e porta sull'elemento l'`ariaLabel` che la documentazione di PrimeNG
 * suggerisce e che il componente non usa.
 *
 * Copre anche i ToggleButton che SelectButton crea nel proprio template, dove una direttiva dell'applicazione
 * non arriva: li cerca sotto l'host del SelectButton e li segue quando cambiano opzioni o stato.
 *
 * Da togliere quando PrimeNG tornerà a dare il ruolo da sé, nella fase 5 dell'aggiornamento della piattaforma.
 */
@Directive({
    selector: 'p-togglebutton, p-toggleButton, p-toggle-button, p-selectbutton, p-selectButton, p-select-button',
    standalone: true
})
export class ToggleButtonRoleDirective implements AfterViewInit, OnChanges, OnDestroy {
    /** Lo stesso input di ToggleButton: per un ToggleButton diventa il nome dell'elemento. */
    @Input() ariaLabel?: string;

    private readonly host: HTMLElement = inject(ElementRef).nativeElement;
    private observer?: MutationObserver;

    ngAfterViewInit(): void {
        this.apply();
        this.observer = new MutationObserver(() => this.apply());
        this.observer.observe(this.host, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-p-disabled'] });
    }

    ngOnChanges(): void {
        if (this.observer) this.apply();
    }

    ngOnDestroy(): void {
        this.observer?.disconnect();
    }

    private apply(): void {
        if (this.host.matches(TOGGLE_BUTTON)) {
            asButton(this.host);
            if (this.ariaLabel) setAttribute(this.host, 'aria-label', this.ariaLabel);
            return;
        }
        this.host.querySelectorAll<HTMLElement>(TOGGLE_BUTTON).forEach(asButton);
    }
}

const asButton = (element: HTMLElement): void => {
    setAttribute(element, 'role', 'button');
    if (element.getAttribute('data-p-disabled') === 'true') setAttribute(element, 'aria-disabled', 'true');
    else element.removeAttribute('aria-disabled');
};

/** Scrive l'attributo solo se cambia, per non toccare il DOM a ogni mutazione osservata. */
const setAttribute = (element: HTMLElement, name: string, value: string): void => {
    if (element.getAttribute(name) !== value) element.setAttribute(name, value);
};
