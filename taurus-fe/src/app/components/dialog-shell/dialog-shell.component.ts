import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonModule } from 'primeng/button';

@Component({ selector: 'app-dialog-shell', standalone: true, imports: [ButtonModule], templateUrl: './dialog-shell.component.html', styleUrl: './dialog-shell.component.scss' })
export class DialogShellComponent {
    @Input() title = '';
    @Input() subtitle?: string;
    @Input() confirmLabel = 'Salva';
    @Input() cancelLabel = 'Annulla';
    @Input() saving = false;
    @Input() invalidCount = 0;
    @Output() confirm = new EventEmitter<void>();
    @Output() cancel = new EventEmitter<void>();
}
