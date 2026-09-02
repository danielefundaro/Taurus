import { booleanAttribute, Component, Input } from '@angular/core';

@Component({ selector: 'app-list-row', standalone: true, templateUrl: './list-row.component.html', styleUrl: './list-row.component.scss' })
export class ListRowComponent {
    @Input({ transform: booleanAttribute }) emphasized = false;
    /** Densità ridotta per i widget della dashboard, dove la riga vive in una colonna. */
    @Input({ transform: booleanAttribute }) compact = false;
}
