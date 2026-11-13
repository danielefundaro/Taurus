import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';
import { ButtonModule } from 'primeng/button';

export interface DangerZoneOperation {
    id: string;
    title: string;
    consequence: string;
    label: string;
    icon?: string;
}
@Component({ selector: 'app-danger-zone', standalone: true, imports: [ButtonModule], templateUrl: './danger-zone.component.html', changeDetection: ChangeDetectionStrategy.Eager, styleUrl: './danger-zone.component.scss' })
export class DangerZoneComponent {
    @Input() operations: DangerZoneOperation[] = [];
    @Output() execute = new EventEmitter<string>();
}
