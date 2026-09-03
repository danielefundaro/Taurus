import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { FinancialAccount, FinancialAttachment, FinancialCategory, FinancialEventSummary, FinancialMovement } from '../../module';
import { ConfirmService, FinanceService, ToastService, parseIsoDate, toIsoDate } from '../../service';

export interface MovementDialogResult {
    movement: FinancialMovement;
    pendingAttachment?: File;
    attachmentDescription?: string;
}

@Component({
    selector: 'app-finance-movement-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './finance-movement-dialog.component.html',
    styleUrl: './finance-movement-dialog.component.scss'
})
export class FinanceMovementDialogComponent {
    protected readonly movement: FinancialMovement;
    protected readonly accounts: FinancialAccount[];
    protected readonly events: FinancialEventSummary[];
    protected bookingDate: Date;
    protected valueDate?: Date;
    protected attachments: FinancialAttachment[] = [];
    protected selectedAttachment?: File;
    protected attachmentDescription = '';

    protected readonly directions = [
        { label: 'Entrata', value: 'INCOME' },
        { label: 'Uscita', value: 'EXPENSE' }
    ];

    private readonly categories: FinancialCategory[];

    constructor(
        private readonly dialogRef: DynamicDialogRef<FinanceMovementDialogComponent>,
        private readonly config: DynamicDialogConfig<
            any,
            {
                movement?: FinancialMovement;
                accounts: FinancialAccount[];
                categories: FinancialCategory[];
                events: FinancialEventSummary[];
                eventId?: number;
            }
        >,
        private readonly financeService: FinanceService,
        private readonly toastService: ToastService,
        private readonly confirmService: ConfirmService
    ) {
        const values = this.config.inputValues;
        this.accounts = values?.accounts ?? [];
        this.categories = values?.categories ?? [];
        this.events = values?.events ?? [];
        this.movement = values?.movement ? { ...values.movement } : { accountId: 0, direction: 'EXPENSE', bookingDate: toIsoDate(new Date()), amount: 0, description: '' };
        if (!this.movement.id && values?.eventId) this.movement.eventId = values.eventId;
        this.bookingDate = parseIsoDate(this.movement.bookingDate) ?? new Date();
        this.valueDate = parseIsoDate(this.movement.valueDate);
        if (this.movement.id) this.loadAttachments(this.movement.id);
    }

    protected get editing(): boolean {
        return !!this.movement.id;
    }

    protected get activeAccounts(): FinancialAccount[] {
        return this.accounts.filter((account) => account.active !== false);
    }

    protected get movementCategories(): FinancialCategory[] {
        return this.categories.filter((category) => category.active !== false && (category.direction === 'BOTH' || category.direction === this.movement.direction));
    }

    protected get accountError(): string | undefined {
        return this.movement.accountId ? undefined : 'Scegli il conto su cui registrare il movimento.';
    }

    protected get amountError(): string | undefined {
        return this.movement.amount > 0 ? undefined : 'Indica un importo maggiore di zero.';
    }

    protected get descriptionError(): string | undefined {
        return this.movement.description?.trim() ? undefined : 'Descrivi a cosa si riferisce il movimento.';
    }

    protected get invalidCount(): number {
        return (this.accountError ? 1 : 0) + (this.amountError ? 1 : 0) + (this.descriptionError ? 1 : 0) + (this.bookingDate ? 0 : 1);
    }

    protected onAttachmentSelected(event: Event): void {
        this.selectedAttachment = (event.target as HTMLInputElement).files?.[0];
    }

    protected uploadSelectedAttachment(): void {
        if (!this.movement.id || !this.selectedAttachment) return;
        this.financeService
            .uploadAttachment(this.movement.id, this.selectedAttachment, this.attachmentDescription)
            .pipe(first())
            .subscribe(() => {
                this.selectedAttachment = undefined;
                this.attachmentDescription = '';
                this.toastService.success('Allegato caricato', 'Il documento è stato associato al movimento.');
                this.loadAttachments(this.movement.id!);
            });
    }

    protected downloadAttachment(attachment: FinancialAttachment): void {
        this.financeService
            .downloadAttachment(attachment.id)
            .pipe(first())
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                const anchor = document.createElement('a');
                anchor.href = url;
                anchor.download = attachment.fileName;
                anchor.click();
                URL.revokeObjectURL(url);
            });
    }

    protected deleteAttachment(attachment: FinancialAttachment): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina allegato',
            consequence: `“${attachment.fileName}” non sarà più consultabile dal movimento.`,
            actionLabel: 'Elimina definitivamente',
            accept: () =>
                this.financeService
                    .deleteAttachment(attachment.id)
                    .pipe(first())
                    .subscribe(() => this.loadAttachments(attachment.movementId))
        });
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (this.invalidCount > 0) return;
        const result: MovementDialogResult = {
            movement: {
                ...this.movement,
                description: this.movement.description.trim(),
                bookingDate: toIsoDate(this.bookingDate),
                valueDate: this.valueDate ? toIsoDate(this.valueDate) : undefined
            },
            pendingAttachment: this.movement.id ? undefined : this.selectedAttachment,
            attachmentDescription: this.attachmentDescription
        };
        this.dialogRef.close(result);
    }

    private loadAttachments(movementId: number): void {
        this.financeService
            .getAttachments(movementId)
            .pipe(first())
            .subscribe((attachments) => (this.attachments = attachments));
    }
}
