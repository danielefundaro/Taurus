import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';

@Component({
    selector: 'app-page-header',
    standalone: true,
    imports: [CommonModule, RouterModule, ButtonModule, TagModule],
    templateUrl: './page-header.component.html',
    styleUrl: './page-header.component.scss'
})
export class PageHeaderComponent {
    @Input({ required: true }) title = '';
    @Input() subtitle?: string;
    @Input() kicker?: string;
    @Input() backLink?: string | any[];
    @Input() state?: string;
    @Input() dirty = false;
}
