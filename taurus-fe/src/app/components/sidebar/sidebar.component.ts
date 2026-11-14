import { Component, ElementRef, ChangeDetectionStrategy } from '@angular/core';
import { MenuComponent } from '../menu/menu.component';

@Component({
    selector: 'app-sidebar',
    standalone: true,
    imports: [MenuComponent],
    templateUrl: './sidebar.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
    constructor(public el: ElementRef) {}
}
