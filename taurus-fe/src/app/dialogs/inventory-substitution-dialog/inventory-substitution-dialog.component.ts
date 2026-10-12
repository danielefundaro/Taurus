import { Component, OnInit } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { first } from 'rxjs';
import { addMonths, inventoryLoanTypeLabel, toLocalDate } from '../../constants';
import { ImportsModule } from '../../imports';
import { InventoryAssignment, InventoryCondition, InventoryItem, InventoryLoanSheetTerms, InventorySubstitutionRequest } from '../../module';
import { InventoryService } from '../../service';

export interface InventorySubstitutionDialogData {
    assignment: InventoryAssignment;
    terms: InventoryLoanSheetTerms;
}

@Component({
    selector: 'app-inventory-substitution-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-substitution-dialog.component.html'
})
export class InventorySubstitutionDialogComponent implements OnInit {
    protected readonly assignment: InventoryAssignment;
    protected readonly terms: InventoryLoanSheetTerms;
    protected items: InventoryItem[] = [];
    protected item?: InventoryItem;
    protected quantity: number;
    protected expirationDate?: Date | null;
    protected accessories = '';
    protected description = '';
    protected returnCondition?: InventoryCondition;
    protected returnNotes = '';
    protected readonly loanTypeLabel = inventoryLoanTypeLabel;
    protected readonly today = new Date();
    protected readonly conditions: { label: string; value: InventoryCondition }[] = [
        { label: 'Nuovo', value: 'NEW' },
        { label: 'Eccellente', value: 'EXCELLENT' },
        { label: 'Buono', value: 'GOOD' },
        { label: 'Discreto', value: 'FAIR' },
        { label: 'Da riparare', value: 'TO_REPAIR' },
        { label: 'Fuori servizio', value: 'OUT_OF_SERVICE' }
    ];

    constructor(
        private readonly dialogRef: DynamicDialogRef<InventorySubstitutionDialogComponent>,
        private readonly inventoryService: InventoryService,
        config: DynamicDialogConfig<InventorySubstitutionDialogData>
    ) {
        this.assignment = config.data!.assignment;
        this.terms = config.data!.terms;
        this.quantity = this.assignment.outstandingQuantity;
    }

    ngOnInit(): void {
        this.inventoryService
            .getItems('', 0, 1000, 'name,asc')
            .pipe(first())
            .subscribe((page) => (this.items = page.content.filter((item) => (item.availableQuantity ?? 0) > 0 && !!item.loanType)));
    }

    protected get fixedTerm(): boolean {
        return this.item?.loanType === 'FIXED_TERM';
    }

    protected get minimumExpiration(): Date {
        return addMonths(this.today, this.terms.minMonths);
    }

    protected get maximumExpiration(): Date {
        return addMonths(this.today, this.terms.maxMonths);
    }

    protected get invalidCount(): number {
        let invalid = this.item ? 0 : 1;
        if (this.quantity < 1 || (this.item && this.quantity > (this.item.availableQuantity ?? 0))) invalid++;
        if (this.fixedTerm && !this.expirationDate) invalid++;
        return invalid;
    }

    protected selectItem(): void {
        if (!this.item) return;
        this.quantity = Math.min(this.quantity, this.item.availableQuantity ?? 1) || 1;
        this.expirationDate = this.fixedTerm ? this.maximumExpiration : undefined;
    }

    protected save(): void {
        if (this.invalidCount || !this.item?.id) return;
        const request: InventorySubstitutionRequest = {
            itemId: this.item.id,
            quantity: this.quantity,
            expirationDate: this.fixedTerm ? toLocalDate(this.expirationDate) : undefined,
            accessories: this.accessories.trim() || undefined,
            description: this.description.trim() || undefined,
            returnCondition: this.returnCondition,
            returnNotes: this.returnNotes.trim() || undefined
        };
        this.dialogRef.close(request);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }
}
