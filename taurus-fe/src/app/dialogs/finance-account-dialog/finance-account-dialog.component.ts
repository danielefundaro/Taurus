import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { FinancialAccount } from '../../module';
import { toIsoDate } from '../../service';

@Component({
    selector: 'app-finance-account-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './finance-account-dialog.component.html'
})
export class FinanceAccountDialogComponent {
    protected readonly account: FinancialAccount;
    protected openingDate = new Date();

    protected readonly accountTypes = [
        { label: 'Cassa', value: 'CASH' },
        { label: 'Conto corrente', value: 'BANK' }
    ];

    constructor(
        private readonly dialogRef: DynamicDialogRef<FinanceAccountDialogComponent>,
        private readonly config: DynamicDialogConfig<any, { account?: FinancialAccount }>
    ) {
        const existing = this.config.inputValues?.account;
        this.account = existing ? { ...existing } : { name: '', accountType: 'CASH', currency: 'EUR', displayOrder: 0, active: true };
    }

    protected get editing(): boolean {
        return !!this.account.id;
    }

    protected get nameError(): string | undefined {
        return this.account.name?.trim() ? undefined : 'Indica come si chiama il conto.';
    }

    protected get currencyError(): string | undefined {
        return /^[A-Za-z]{3}$/.test(this.account.currency ?? '') ? undefined : 'Serve un codice valuta di tre lettere, per esempio EUR.';
    }

    protected get invalidCount(): number {
        return (this.nameError ? 1 : 0) + (this.currencyError ? 1 : 0);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (this.invalidCount > 0) return;
        this.account.name = this.account.name.trim();
        this.account.currency = this.account.currency.trim().toUpperCase();
        if (!this.account.id && this.account.initialBalance) this.account.initialBalanceDate = toIsoDate(this.openingDate);
        this.dialogRef.close(this.account);
    }
}
