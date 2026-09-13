import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { ConfirmService } from '../../service/confirm.service';

@Component({ selector: 'app-dialog-shell', standalone: true, imports: [ButtonModule], templateUrl: './dialog-shell.component.html', styleUrl: './dialog-shell.component.scss' })
export class DialogShellComponent {
    @Input() heading = '';
    @Input() subtitle?: string;
    @Input() confirmLabel = 'Salva';
    @Input() cancelLabel = 'Annulla';
    @Input() saving = false;
    @Input() invalidCount = 0;
    @Input() dirty = false;
    @Output() confirm = new EventEmitter<void>();
    @Output() cancel = new EventEmitter<void>();

    validationAttempted = false;
    private interacted = false;
    private confirmationOpen = false;

    constructor(private readonly confirmService: ConfirmService) { }

    @HostListener('input')
    @HostListener('change')
    markAsInteracted(): void {
        this.interacted = true;
    }

    @HostListener('click', ['$event'])
    markContentClickAsInteracted(event: MouseEvent): void {
        const target = event.target;
        if (target instanceof HTMLElement && !target.closest('header, footer')) this.interacted = true;
    }

    @HostListener('document:keydown.escape', ['$event'])
    handleEscape(event: KeyboardEvent): void {
        event.preventDefault();
        event.stopPropagation();
        this.requestCancel();
    }

    @HostListener('document:click', ['$event'])
    handleMaskClick(event: MouseEvent): void {
        const target = event.target;
        if (target instanceof HTMLElement && target.classList.contains('p-dialog-mask')) this.requestCancel();
    }

    submit(): void {
        this.validationAttempted = true;
        if (this.invalidCount === 0) this.confirm.emit();
    }

    requestCancel(): void {
        if (this.saving || this.confirmationOpen) return;
        if (!this.dirty && !this.interacted) {
            this.cancel.emit();
            return;
        }

        this.confirmationOpen = true;
        this.confirmService.confirmDiscard({
            title: 'Modifiche non salvate',
            consequence: 'Le modifiche inserite nel modulo andranno perse.',
            actionLabel: 'Esci senza salvare',
            accept: () => {
                this.confirmationOpen = false;
                this.cancel.emit();
            },
            reject: () => {
                this.confirmationOpen = false;
            }
        });
    }
}
