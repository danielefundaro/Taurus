import { booleanAttribute, Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonModule } from 'primeng/button';

@Component({ selector: 'app-empty-state', standalone: true, imports: [ButtonModule], templateUrl: './empty-state.component.html', styleUrl: './empty-state.component.scss' })
export class EmptyStateComponent {
    @Input() icon = 'pi pi-inbox';
    @Input() title = 'Nessun elemento';
    @Input() message = '';
    @Input() actionLabel?: string;
    @Input({ transform: booleanAttribute }) compact = false;
    @Output() action = new EventEmitter<void>();
}
