import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { FinancialAccount, FinancialTransferRequest } from '../../module';
import { toIsoDate } from '../../service';

@Component({
    selector: 'app-finance-transfer-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './finance-transfer-dialog.component.html'
})
export class FinanceTransferDialogComponent {
    protected readonly accounts: FinancialAccount[];
    protected readonly transfer: FinancialTransferRequest;
    protected bookingDate = new Date();
    protected valueDate?: Date;

    constructor(
        private readonly dialogRef: DynamicDialogRef<FinanceTransferDialogComponent>,
        private readonly config: DynamicDialogConfig<any, { accounts: FinancialAccount[] }>
    ) {
        this.accounts = this.config.inputValues?.accounts ?? [];
        this.transfer = {
            sourceAccountId: 0,
            destinationAccountId: 0,
            bookingDate: toIsoDate(this.bookingDate),
            amount: 0,
            description: ''
        };
    }

    protected get sourceError(): string | undefined {
        return this.transfer.sourceAccountId ? undefined : 'Scegli il conto di origine.';
    }

    protected get destinationError(): string | undefined {
        if (!this.transfer.destinationAccountId) return 'Scegli il conto di destinazione.';
        if (this.transfer.destinationAccountId === this.transfer.sourceAccountId) return 'I due conti devono essere diversi.';
        return undefined;
    }

    protected get amountError(): string | undefined {
        return this.transfer.amount > 0 ? undefined : 'Indica un importo maggiore di zero.';
    }

    protected get descriptionError(): string | undefined {
        return this.transfer.description?.trim() ? undefined : 'Descrivi il motivo del trasferimento.';
    }

    protected get invalidCount(): number {
        return (this.sourceError ? 1 : 0) + (this.destinationError ? 1 : 0) + (this.amountError ? 1 : 0) + (this.descriptionError ? 1 : 0);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (this.invalidCount > 0) return;
        this.transfer.description = this.transfer.description.trim();
        this.transfer.bookingDate = toIsoDate(this.bookingDate);
        this.transfer.valueDate = this.valueDate ? toIsoDate(this.valueDate) : undefined;
        this.dialogRef.close(this.transfer);
    }
}
