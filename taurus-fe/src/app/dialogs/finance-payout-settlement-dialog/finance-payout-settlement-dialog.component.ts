import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { FinancialAccount, FinancialCategory } from '../../module';
import { toIsoDate } from '../../service';

/** Una riga che il movimento salderà: l'evento e l'importo scelto nella vista del socio. */
export interface PayoutSettlementDialogLine {
    eventName: string;
    eventDate: string;
    amount: number;
}

export interface PayoutSettlementDialogData {
    memberName: string;
    lines: PayoutSettlementDialogLine[];
    total: number;
    accounts: FinancialAccount[];
    categories: FinancialCategory[];
}

export interface PayoutSettlementDialogResult {
    accountId: number;
    categoryId?: number | null;
    bookingDate: string;
    description?: string | null;
    notes?: string | null;
}

/** Conto, data e descrizione dell'unico movimento di uscita che salda le quote scelte di un socio. */
@Component({
    selector: 'app-finance-payout-settlement-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './finance-payout-settlement-dialog.component.html'
})
export class FinancePayoutSettlementDialogComponent {
    protected readonly memberName: string;
    protected readonly lines: PayoutSettlementDialogLine[];
    protected readonly total: number;
    protected readonly accounts: FinancialAccount[];
    protected readonly categories: FinancialCategory[];
    protected accountId?: number;
    protected categoryId?: number | null;
    protected bookingDate = new Date();
    protected description = '';
    protected notes = '';

    constructor(
        private readonly dialogRef: DynamicDialogRef<FinancePayoutSettlementDialogComponent>,
        private readonly config: DynamicDialogConfig<any, PayoutSettlementDialogData>
    ) {
        const data = this.config.inputValues;
        this.memberName = data?.memberName ?? '';
        this.lines = data?.lines ?? [];
        this.total = data?.total ?? 0;
        this.accounts = (data?.accounts ?? []).filter((account) => account.active !== false);
        // Solo le categorie di uscita attive: il movimento è sempre un'uscita.
        this.categories = (data?.categories ?? []).filter((category) => category.active !== false && category.direction !== 'INCOME');
        if (this.accounts.length === 1) this.accountId = this.accounts[0].id;
    }

    protected get eventsLabel(): string {
        return this.lines.length === 1 ? '1 evento' : `${this.lines.length} eventi`;
    }

    protected get accountError(): string | undefined {
        return this.accountId ? undefined : 'Scegli il conto da cui esce il pagamento.';
    }

    protected get dateError(): string | undefined {
        return this.bookingDate ? undefined : 'Indica la data contabile.';
    }

    protected get invalidCount(): number {
        return (this.accountError ? 1 : 0) + (this.dateError ? 1 : 0);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (this.invalidCount > 0 || !this.accountId) return;
        const result: PayoutSettlementDialogResult = {
            accountId: this.accountId,
            categoryId: this.categoryId ?? null,
            bookingDate: toIsoDate(this.bookingDate),
            description: this.description.trim() || null,
            notes: this.notes.trim() || null
        };
        this.dialogRef.close(result);
    }
}
