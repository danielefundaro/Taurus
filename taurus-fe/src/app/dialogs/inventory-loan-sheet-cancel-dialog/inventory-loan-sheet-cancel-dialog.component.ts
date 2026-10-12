import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { InventoryLoanSheetSummary } from '../../module';

@Component({
    selector: 'app-inventory-loan-sheet-cancel-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-loan-sheet-cancel-dialog.component.html'
})
export class InventoryLoanSheetCancelDialogComponent {
    protected readonly sheet: InventoryLoanSheetSummary;
    protected reason = '';
    protected confirmed = false;

    constructor(
        private readonly dialogRef: DynamicDialogRef<InventoryLoanSheetCancelDialogComponent>,
        config: DynamicDialogConfig<{ sheet: InventoryLoanSheetSummary }>
    ) {
        this.sheet = config.data!.sheet;
    }

    protected save(): void {
        if (this.reason.trim() && this.confirmed) this.dialogRef.close(this.reason.trim());
    }

    protected cancel(): void {
        this.dialogRef.close();
    }
}
