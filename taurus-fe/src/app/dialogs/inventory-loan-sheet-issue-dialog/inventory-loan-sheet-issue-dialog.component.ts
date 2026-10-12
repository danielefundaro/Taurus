import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { finalize, first } from 'rxjs';
import { fromLocalDate, inventoryLoanTypeLabel, loanDurationIssueMessage, toLocalDate } from '../../constants';
import { ImportsModule } from '../../imports';
import { InventoryLoanHolderRole, InventoryLoanSheetIssueRequest, InventoryLoanSheetProposal, InventoryLoanSheetProposalAssignment } from '../../module';
import { InventoryService } from '../../service';

export interface InventoryLoanSheetIssueDialogData {
    userIndex: number;
    proposal: InventoryLoanSheetProposal;
}

@Component({
    selector: 'app-inventory-loan-sheet-issue-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-loan-sheet-issue-dialog.component.html'
})
export class InventoryLoanSheetIssueDialogComponent {
    protected readonly proposal: InventoryLoanSheetProposal;
    protected readonly userIndex: number;
    protected readonly selected: Record<number, boolean> = {};
    protected issuedOn?: Date;
    protected place = '';
    protected holderRole?: InventoryLoanHolderRole;
    protected authorizationReference = '';
    protected authorizationDate?: Date | null;
    protected associationSignatoryName = '';
    protected previewing = false;
    protected readonly loanTypeLabel = inventoryLoanTypeLabel;
    protected readonly issueMessage = loanDurationIssueMessage;
    protected readonly roles: { label: string; value: InventoryLoanHolderRole }[] = [
        { label: 'Socio maggiorenne', value: 'MEMBER' },
        { label: 'Genitore o tutore', value: 'GUARDIAN' }
    ];

    constructor(
        private readonly dialogRef: DynamicDialogRef<InventoryLoanSheetIssueDialogComponent>,
        private readonly inventoryService: InventoryService,
        config: DynamicDialogConfig<InventoryLoanSheetIssueDialogData>
    ) {
        this.proposal = config.data!.proposal;
        this.userIndex = config.data!.userIndex;
        this.proposal.assignments.forEach((assignment) => (this.selected[assignment.assignmentId] = true));
        this.issuedOn = fromLocalDate(this.proposal.issuedOn);
        this.place = this.proposal.place ?? '';
        this.holderRole = this.proposal.suggestedRole;
        this.associationSignatoryName = this.proposal.associationSignatoryName ?? '';
    }

    protected get selectedAssignments(): InventoryLoanSheetProposalAssignment[] {
        return this.proposal.assignments.filter((assignment) => this.selected[assignment.assignmentId]);
    }

    protected get blockingIssues(): number {
        return this.selectedAssignments.reduce((total, assignment) => total + assignment.issues.length, 0);
    }

    protected get invalidCount(): number {
        return (this.selectedAssignments.length ? 0 : 1) + this.blockingIssues + (this.issuedOn ? 0 : 1) + (this.holderRole ? 0 : 1);
    }

    protected preview(): void {
        if (!this.selectedAssignments.length || !this.issuedOn) return;
        this.previewing = true;
        this.inventoryService
            .previewLoanSheet(this.userIndex, this.request())
            .pipe(
                first(),
                finalize(() => (this.previewing = false))
            )
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                window.open(url, '_blank', 'noopener');
                setTimeout(() => URL.revokeObjectURL(url), 60_000);
            });
    }

    protected save(): void {
        if (this.invalidCount) return;
        this.dialogRef.close(this.request());
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    private request(): InventoryLoanSheetIssueRequest {
        return {
            assignmentIds: this.selectedAssignments.map((assignment) => assignment.assignmentId),
            issuedOn: toLocalDate(this.issuedOn)!,
            place: this.place.trim() || undefined,
            holderRole: this.holderRole,
            authorizationReference: this.authorizationReference.trim() || undefined,
            authorizationDate: toLocalDate(this.authorizationDate),
            associationSignatoryName: this.associationSignatoryName.trim() || undefined
        };
    }
}
