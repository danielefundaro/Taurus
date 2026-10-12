import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { toLocalDate } from '../../constants';
import { ImportsModule } from '../../imports';
import { InventoryLoanReturnOutcome, InventoryLoanSheetCloseRequest, InventoryLoanSheetSummary } from '../../module';

@Component({
    selector: 'app-inventory-loan-sheet-close-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-loan-sheet-close-dialog.component.html'
})
export class InventoryLoanSheetCloseDialogComponent {
    protected readonly sheet: InventoryLoanSheetSummary;
    protected outcome: InventoryLoanReturnOutcome = 'COMPLIANT';
    protected chargeAmount?: number | null;
    protected chargeDueDate?: Date | null;
    protected receivedByName = '';
    protected readonly outcomes: { label: string; value: InventoryLoanReturnOutcome }[] = [
        { label: 'Nessun addebito', value: 'COMPLIANT' },
        { label: 'Danni o mancanze', value: 'CHARGES' }
    ];

    constructor(
        private readonly dialogRef: DynamicDialogRef<InventoryLoanSheetCloseDialogComponent>,
        config: DynamicDialogConfig<{ sheet: InventoryLoanSheetSummary }>
    ) {
        this.sheet = config.data!.sheet;
    }

    protected get charges(): boolean {
        return this.outcome === 'CHARGES';
    }

    protected get invalidCount(): number {
        if (!this.charges) return 0;
        return (this.chargeAmount && this.chargeAmount > 0 ? 0 : 1) + (this.chargeDueDate ? 0 : 1);
    }

    protected save(): void {
        if (this.invalidCount) return;
        const request: InventoryLoanSheetCloseRequest = {
            outcome: this.outcome,
            chargeAmount: this.charges ? this.chargeAmount : undefined,
            chargeDueDate: this.charges ? toLocalDate(this.chargeDueDate) : undefined,
            receivedByName: this.receivedByName.trim() || undefined
        };
        this.dialogRef.close(request);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }
}
