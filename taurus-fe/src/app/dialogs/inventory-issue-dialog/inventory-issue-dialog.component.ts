import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { InventoryIssue, InventoryIssueSeverity } from '../../module';
import { InventoryService, UserInventoryService } from '../../service';

@Component({
    selector: 'app-inventory-issue-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-issue-dialog.component.html'
})
export class InventoryIssueDialogComponent {
    protected readonly severities = [
        { label: 'Minore — difetto non bloccante', value: 'MINOR' },
        { label: 'Limitante — riparazione necessaria', value: 'LIMITING' },
        { label: 'Non sicuro — possibile rischio', value: 'UNSAFE' }
    ];
    protected quantity = 1;
    protected severity: InventoryIssueSeverity = 'MINOR';
    protected description = '';
    protected saving = false;
    private readonly itemId?: number;
    private readonly assignmentId?: number;
    protected readonly maxQuantity: number;

    constructor(
        private readonly dialogRef: DynamicDialogRef<InventoryIssueDialogComponent>,
        config: DynamicDialogConfig<{ itemId?: number; assignmentId?: number; maxQuantity: number }>,
        private readonly inventoryService: InventoryService,
        private readonly userInventoryService: UserInventoryService
    ) {
        this.itemId = config.data?.itemId;
        this.assignmentId = config.data?.assignmentId;
        this.maxQuantity = config.data?.maxQuantity ?? 1;
    }

    protected get invalidCount(): number {
        return (this.quantity < 1 || this.quantity > this.maxQuantity ? 1 : 0) + (this.description.trim() ? 0 : 1);
    }

    protected save(): void {
        if (this.invalidCount || this.saving) return;
        const request = this.itemId
            ? this.inventoryService.createIssue(this.itemId, this.quantity, this.severity, this.description.trim())
            : this.userInventoryService.createIssue(this.assignmentId!, this.quantity, this.severity, this.description.trim());
        this.saving = true;
        request.pipe(first()).subscribe({
            next: (issue: InventoryIssue) => this.dialogRef.close(issue),
            error: () => (this.saving = false)
        });
    }

    protected cancel(): void {
        this.dialogRef.close();
    }
}
