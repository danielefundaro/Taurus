import { AfterViewInit, Directive, ElementRef, inject, OnDestroy } from '@angular/core';

/** L'elemento di un ToggleButton di PrimeNG, nei due selettori che la 22 accetta. */
const TOGGLE_BUTTON = 'p-togglebutton, p-toggle-button';

/**
 * Segnala con `aria-disabled` i ToggleButton disabilitati. Da PrimeNG 21 il componente ha di nuovo il ruolo di
 * pulsante e prende il nome dall'`ariaLabel`, ma lo stato disabilitato resta solo in `data-p-disabled` e nel
 * `tabindex`: senza `aria-disabled` i lettori di schermo lo annunciano come un pulsante attivo.
 *
 * Copre anche i ToggleButton che SelectButton crea nel proprio template, dove una direttiva dell'applicazione
 * non arriva: li cerca sotto l'host del SelectButton e li segue quando cambiano opzioni o stato.
 *
 * Nata nella fase 0 dell'aggiornamento della piattaforma come ToggleButtonRoleDirective, quando PrimeNG 19.1 aveva
 * tolto anche il ruolo; da togliere quando PrimeNG scriverà `aria-disabled` da sé: la 22 non lo scrive ancora, e
 * non riconosce più i selettori in camel case.
 */
@Directive({
    selector: 'p-togglebutton, p-toggle-button, p-selectbutton, p-select-button',
    standalone: true
})
export class ToggleButtonDisabledDirective implements AfterViewInit, OnDestroy {
    private readonly host: HTMLElement = inject(ElementRef).nativeElement;
    private observer?: MutationObserver;

    ngAfterViewInit(): void {
        this.apply();
        this.observer = new MutationObserver(() => this.apply());
        this.observer.observe(this.host, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-p-disabled'] });
    }

    ngOnDestroy(): void {
        this.observer?.disconnect();
    }

    private apply(): void {
        if (this.host.matches(TOGGLE_BUTTON)) {
            markDisabled(this.host);
            return;
        }
        this.host.querySelectorAll<HTMLElement>(TOGGLE_BUTTON).forEach(markDisabled);
    }
}

/** Scrive o toglie `aria-disabled` solo se cambia, per non toccare il DOM a ogni mutazione osservata. */
const markDisabled = (element: HTMLElement): void => {
    const disabled = element.getAttribute('data-p-disabled') === 'true';

    if (disabled && element.getAttribute('aria-disabled') !== 'true') element.setAttribute('aria-disabled', 'true');
    else if (!disabled && element.hasAttribute('aria-disabled')) element.removeAttribute('aria-disabled');
};
