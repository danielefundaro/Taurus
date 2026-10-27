/**
 * Porta lo scroll e il focus sulla sezione nominata dal frammento dell'URL, per le azioni della scansione che aprono
 * un punto preciso della scheda. Il contenuto arriva dopo una chiamata HTTP: il frammento si cerca al prossimo ciclo,
 * quando il template lo ha già disegnato. Una sezione assente — feature spenta, vista diversa — non fa nulla.
 */
export function focusInventoryFragment(fragment: string | null | undefined, documentRef: Document = document): void {
    if (!fragment) return;
    setTimeout(() => {
        const target = documentRef.getElementById(fragment);
        if (!target) return;
        if (!target.hasAttribute('tabindex')) target.setAttribute('tabindex', '-1');
        target.scrollIntoView({ block: 'start' });
        target.focus({ preventScroll: true });
    });
}
