import { Component, Input, OnChanges } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { first } from 'rxjs';
import { InventoryIssueDialogComponent } from '../../dialogs/inventory-issue-dialog/inventory-issue-dialog.component';
import { ImportsModule } from '../../imports';
import { InventoryCondition, InventoryIssue, InventoryIssueStatus } from '../../module';
import { InventoryService, ToastService } from '../../service';

@Component({
    selector: 'app-inventory-issue-list',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-issue-list.component.html',
    styleUrl: './inventory-issue-list.component.scss',
    providers: [DialogService]
})
export class InventoryIssueListComponent implements OnChanges {
    @Input({ required: true }) itemId!: number;
    @Input({ required: true }) totalQuantity = 1;
    protected issues: InventoryIssue[] = [];
    protected loading = false;
    protected resolutionNotes: Record<number, string> = {};
    protected resolutionConditions: Record<number, InventoryCondition | undefined> = {};
    protected readonly conditions = [
        { label: 'Non modificare lo stato del bene', value: undefined },
        { label: 'Nuovo', value: 'NEW' },
        { label: 'Eccellente', value: 'EXCELLENT' },
        { label: 'Buono', value: 'GOOD' },
        { label: 'Discreto', value: 'FAIR' },
        { label: 'Da riparare', value: 'TO_REPAIR' },
        { label: 'Fuori servizio', value: 'OUT_OF_SERVICE' }
    ];

    constructor(
        private readonly inventoryService: InventoryService,
        private readonly dialogService: DialogService,
        private readonly toastService: ToastService
    ) {}

    ngOnChanges(): void {
        if (this.itemId) this.load();
    }

    protected create(): void {
        this.dialogService
            .open(InventoryIssueDialogComponent, {
                header: 'Segnala guasto',
                showHeader: false,
                modal: true,
                width: '28rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: { itemId: this.itemId, maxQuantity: this.totalQuantity }
            })
            .onClose.pipe(first())
            .subscribe((issue) => {
                if (!issue) return;
                this.toastService.success('Segnalazione inviata', 'Il guasto è stato registrato.');
                this.load();
            });
    }

    protected transition(issue: InventoryIssue, status: InventoryIssueStatus): void {
        this.inventoryService
            .transitionIssue(issue.id, status, issue.version, this.resolutionNotes[issue.id], this.resolutionConditions[issue.id])
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Segnalazione aggiornata', `Nuovo stato: ${this.statusLabel(status)}.`);
                this.load();
            });
    }

    protected upload(issue: InventoryIssue, event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        if (file.size > 10 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)) {
            this.toastService.error('Fotografia non valida', 'Sono ammessi JPEG/PNG fino a 10 MB.');
            input.value = '';
            return;
        }
        this.inventoryService
            .uploadIssuePhoto(issue.id, file)
            .pipe(first())
            .subscribe(() => {
                input.value = '';
                this.load();
            });
    }

    protected photoUrl(id: number): string {
        return this.inventoryService.issuePhotoUrl(id);
    }
    protected severityLabel(issue: InventoryIssue): string {
        return { MINOR: 'Minore', LIMITING: 'Limitante', UNSAFE: 'Non sicuro' }[issue.severity];
    }
    protected statusLabel(status: InventoryIssueStatus): string {
        return { OPEN: 'Aperta', ACKNOWLEDGED: 'Presa in carico', RESOLVED: 'Risolta', DISMISSED: 'Respinta' }[status];
    }
    protected isTerminal(issue: InventoryIssue): boolean {
        return issue.status === 'RESOLVED' || issue.status === 'DISMISSED';
    }
    protected hasResolutionNotes(issue: InventoryIssue): boolean {
        return !!this.resolutionNotes[issue.id]?.trim();
    }

    private load(): void {
        this.loading = true;
        this.inventoryService
            .getIssues(this.itemId)
            .pipe(first())
            .subscribe({
                next: (issues) => {
                    this.issues = issues;
                    this.loading = false;
                },
                error: () => (this.loading = false)
            });
    }
}
