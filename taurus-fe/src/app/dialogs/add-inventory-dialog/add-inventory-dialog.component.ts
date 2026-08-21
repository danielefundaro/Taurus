import { Component } from '@angular/core';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { InventoryCondition, InventoryItem } from '../../module';

@Component({
    selector: 'app-add-inventory-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './add-inventory-dialog.component.html',
    styleUrl: './add-inventory-dialog.component.scss'
})
export class AddInventoryDialogComponent {
    protected readonly item: InventoryItem = {
        inventoryNumber: '',
        name: '',
        totalQuantity: 0,
        estimatedUnitValue: undefined,
        currency: 'EUR',
        conditionStatus: 'GOOD',
        photos: [],
        assignments: []
    };

    protected readonly conditions: { label: string; value: InventoryCondition }[] = [
        { label: 'Nuovo', value: 'NEW' },
        { label: 'Eccellente', value: 'EXCELLENT' },
        { label: 'Buono', value: 'GOOD' },
        { label: 'Discreto', value: 'FAIR' },
        { label: 'Da riparare', value: 'TO_REPAIR' },
        { label: 'Fuori servizio', value: 'OUT_OF_SERVICE' }
    ];

    constructor(private readonly dialogRef: DynamicDialogRef<AddInventoryDialogComponent>) {}

    protected get canSave(): boolean {
        return !!this.item.inventoryNumber.trim() && !!this.item.name.trim();
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (!this.canSave) return;
        this.item.inventoryNumber = this.item.inventoryNumber.trim();
        this.item.name = this.item.name.trim();
        this.item.currency = this.item.currency?.trim().toUpperCase() || 'EUR';
        this.dialogRef.close(this.item);
    }
}
