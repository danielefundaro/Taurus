import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { FormsModule } from '@angular/forms';

@Component({ selector: 'app-selection-bar', standalone: true, imports: [ButtonModule, CheckboxModule, FormsModule], templateUrl: './selection-bar.component.html', styleUrl: './selection-bar.component.scss' })
export class SelectionBarComponent {
    @Input() count = 0;
    @Input() allSelected = false;
    @Input() deleteLabel = 'Elimina selezionati';
    @Input() actionLabel?: string;
    @Input() actionIcon = 'pi pi-trash';
    @Input() actionSeverity: 'secondary' | 'success' | 'info' | 'warn' | 'help' | 'danger' | 'contrast' | null = 'danger';
    @Input() actionLoading = false;
    @Output() toggleAll = new EventEmitter<boolean>();
    @Output() clear = new EventEmitter<void>();
    @Output() delete = new EventEmitter<void>();
    @Output() action = new EventEmitter<void>();

    protected invokeAction(): void {
        if (this.actionLabel) {
            this.action.emit();
        } else {
            this.delete.emit();
        }
    }
}
