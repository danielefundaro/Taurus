import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Params, RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';

export type AlertSeverity = 'success' | 'info' | 'warn' | 'error';
@Component({ selector: 'app-inline-alert', standalone: true, imports: [MessageModule, ButtonModule, RouterModule], templateUrl: './inline-alert.component.html', styleUrl: './inline-alert.component.scss' })
export class InlineAlertComponent {
    @Input() severity: AlertSeverity = 'info';
    @Input() title = '';
    @Input() detail = '';
    @Input() actionLabel?: string;
    @Input() actionLink?: string | any[];
    @Input() queryParams?: Params;
    @Output() action = new EventEmitter<void>();
    get role(): 'alert' | 'status' {
        return this.severity === 'error' ? 'alert' : 'status';
    }
}
