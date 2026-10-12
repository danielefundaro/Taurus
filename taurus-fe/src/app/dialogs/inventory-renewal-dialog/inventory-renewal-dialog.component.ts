import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { addMonths, fromLocalDate, toLocalDate } from '../../constants';
import { ImportsModule } from '../../imports';
import { InventoryAssignment, InventoryLoanSheetTerms, InventoryRenewalRequest } from '../../module';

export interface InventoryRenewalDialogData {
    assignment: InventoryAssignment;
    terms: InventoryLoanSheetTerms;
}

@Component({
    selector: 'app-inventory-renewal-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-renewal-dialog.component.html'
})
export class InventoryRenewalDialogComponent {
    protected readonly assignment: InventoryAssignment;
    protected readonly terms: InventoryLoanSheetTerms;
    protected readonly currentExpiration?: Date;
    protected readonly maximumExpiration?: Date;
    protected newExpirationDate?: Date | null;
    protected authorizationReference = '';
    protected authorizationDate?: Date | null;
    protected renewedOn: Date = new Date();

    constructor(
        private readonly dialogRef: DynamicDialogRef<InventoryRenewalDialogComponent>,
        config: DynamicDialogConfig<InventoryRenewalDialogData>
    ) {
        this.assignment = config.data!.assignment;
        this.terms = config.data!.terms;
        this.currentExpiration = fromLocalDate(this.assignment.expirationDate);
        this.maximumExpiration = this.currentExpiration ? addMonths(this.currentExpiration, this.terms.renewalMaxMonths) : undefined;
        this.newExpirationDate = this.maximumExpiration;
    }

    protected get minimumExpiration(): Date | undefined {
        if (!this.currentExpiration) return undefined;
        const next = new Date(this.currentExpiration);
        next.setDate(next.getDate() + 1);
        return next;
    }

    protected get expired(): boolean {
        return !!this.currentExpiration && this.renewedOn > this.currentExpiration;
    }

    protected get invalidCount(): number {
        let invalid = 0;
        if (!this.newExpirationDate || (this.minimumExpiration && this.newExpirationDate < this.minimumExpiration)) invalid++;
        if (!this.authorizationReference.trim()) invalid++;
        if (!this.authorizationDate || this.authorizationDate > this.renewedOn) invalid++;
        if (this.expired) invalid++;
        return invalid;
    }

    protected save(): void {
        if (this.invalidCount) return;
        const request: InventoryRenewalRequest = {
            newExpirationDate: toLocalDate(this.newExpirationDate)!,
            authorizationReference: this.authorizationReference.trim(),
            authorizationDate: toLocalDate(this.authorizationDate)!,
            renewedOn: toLocalDate(this.renewedOn)
        };
        this.dialogRef.close(request);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }
}
