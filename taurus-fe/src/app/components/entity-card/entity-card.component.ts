import { Component, Input, ChangeDetectionStrategy } from '@angular/core';

@Component({ selector: 'app-entity-card', standalone: true, templateUrl: './entity-card.component.html', changeDetection: ChangeDetectionStrategy.Eager, styleUrl: './entity-card.component.scss' })
export class EntityCardComponent {
    @Input() emphasized = false;
    @Input() selected = false;
}
