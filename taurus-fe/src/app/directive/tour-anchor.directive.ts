import { Directive, Input } from '@angular/core';

/**
 * Marks an element that a step of the guided tour can highlight: `appTourAnchor="calendar.header"`. Anchors are stable
 * identifiers checked by `scripts/check-tour-anchors.mjs`, not CSS selectors.
 */
@Directive({
    selector: '[appTourAnchor]',
    standalone: true,
    host: { '[attr.data-tour-anchor]': 'appTourAnchor' }
})
export class TourAnchorDirective {
    @Input({ required: true }) appTourAnchor!: string;
}
