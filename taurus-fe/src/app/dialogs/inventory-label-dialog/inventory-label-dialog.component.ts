import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { InventoryItem, InventoryLabelLayout, InventoryLabelRequest } from '../../module';

@Component({
    selector: 'app-inventory-label-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-label-dialog.component.html'
})
export class InventoryLabelDialogComponent {
    protected readonly items: InventoryItem[];
    protected readonly layouts = [
        { label: 'Etichetta singola 62 × 40 mm', value: 'SINGLE_62X40' },
        { label: 'Foglio A4, griglia 3 × 8', value: 'A4_GRID_3X8' }
    ];
    protected layout: InventoryLabelLayout = 'A4_GRID_3X8';
    protected startCell = 0;
    protected showCutMarks = false;
    protected copies: Record<number, number> = {};

    constructor(
        private readonly dialogRef: DynamicDialogRef<InventoryLabelDialogComponent>,
        config: DynamicDialogConfig<{ items: InventoryItem[] }>
    ) {
        this.items = config.data?.items ?? [];
        this.items.forEach((item) => {
            if (item.id) this.copies[item.id] = 1;
        });
    }

    protected get totalLabels(): number {
        return this.items.reduce((total, item) => total + (item.id ? this.copies[item.id] || 0 : 0), 0);
    }

    protected get invalidCount(): number {
        let invalid = this.items.length ? 0 : 1;
        invalid += this.items.filter((item) => !item.id || this.copies[item.id] < 1 || this.copies[item.id] > 20).length;
        if (this.layout === 'SINGLE_62X40' && this.totalLabels > 20) invalid++;
        if (this.layout === 'A4_GRID_3X8' && this.totalLabels > 240 - this.startCell) invalid++;
        return invalid;
    }

    protected save(): void {
        if (this.invalidCount) return;
        const request: InventoryLabelRequest = {
            layout: this.layout,
            startCell: this.layout === 'A4_GRID_3X8' ? this.startCell : 0,
            showCutMarks: this.layout === 'A4_GRID_3X8' && this.showCutMarks,
            entries: this.items.map((item) => ({ itemId: item.id!, copies: this.copies[item.id!] }))
        };
        this.dialogRef.close(request);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }
}
