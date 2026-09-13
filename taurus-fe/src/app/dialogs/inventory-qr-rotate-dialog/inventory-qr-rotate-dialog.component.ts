import { Component } from '@angular/core';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';

@Component({
    selector: 'app-inventory-qr-rotate-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-qr-rotate-dialog.component.html'
})
export class InventoryQrRotateDialogComponent {
    protected reason = '';
    protected confirmed = false;

    constructor(private readonly dialogRef: DynamicDialogRef<InventoryQrRotateDialogComponent>) { }

    protected save(): void {
        if (this.reason.trim() && this.confirmed) this.dialogRef.close(this.reason.trim());
    }
    protected cancel(): void {
        this.dialogRef.close();
    }
}
