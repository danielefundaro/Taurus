import { booleanAttribute, Component, Input } from '@angular/core';
import { Params, RouterModule } from '@angular/router';
import { TagModule } from 'primeng/tag';

@Component({
    selector: 'app-detail-section',
    standalone: true,
    imports: [RouterModule, TagModule],
    templateUrl: './detail-section.component.html',
    styleUrl: './detail-section.component.scss'
})
export class DetailSectionComponent {
    @Input({ required: true }) title = '';
    @Input() description?: string;
    @Input() count?: number;
    @Input({ transform: booleanAttribute }) dirty = false;
    /** Collegamento di intestazione, usato dai widget della dashboard. */
    @Input() actionLabel?: string;
    @Input() actionLink?: string | any[];
    @Input() actionQueryParams?: Params;
}
