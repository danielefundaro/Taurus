import { Component, Input } from '@angular/core';

@Component({ selector: 'app-entity-card', standalone: true, templateUrl: './entity-card.component.html', styleUrl: './entity-card.component.scss' })
export class EntityCardComponent {
    @Input() emphasized = false;
    @Input() selected = false;
}
