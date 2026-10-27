import { Component, Input, OnChanges } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { first, Observable } from 'rxjs';
import { InventoryIssueDialogComponent } from '../../dialogs/inventory-issue-dialog/inventory-issue-dialog.component';
import { ImportsModule } from '../../imports';
import { InventoryCondition, InventoryIssue, InventoryIssuePhoto, InventoryIssueStatus } from '../../module';
import { ConfirmService, InventoryService, ToastService, UserInventoryService } from '../../service';

/**
 * Le segnalazioni guasto di un oggetto, nella scheda amministrativa, oppure di una consegna, nella vista personale.
 * Con {@link assignmentId} il componente usa le API personali: niente transizioni né rimozione di fotografie, e le
 * fotografie si aggiungono solo alle segnalazioni ancora aperte.
 */
@Component({
    selector: 'app-inventory-issue-list',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-issue-list.component.html',
    styleUrl: './inventory-issue-list.component.scss',
    providers: [DialogService]
})
export class InventoryIssueListComponent implements OnChanges {
    @Input() itemId?: number;
    @Input() assignmentId?: number;
    /** La quantità massima di una nuova segnalazione: il totale dell'oggetto, o la quantità ancora in carico. */
    @Input({ required: true }) totalQuantity = 1;
    @Input() headingId?: string;
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
        private readonly userInventoryService: UserInventoryService,
        private readonly dialogService: DialogService,
        private readonly confirmService: ConfirmService,
        private readonly toastService: ToastService
    ) { }

    protected get personal(): boolean {
        return !!this.assignmentId;
    }

    ngOnChanges(): void {
        if (this.itemId || this.assignmentId) this.load();
    }

    protected create(): void {
        this.dialogService
            .open(InventoryIssueDialogComponent, {
                header: 'Segnala guasto',
                showHeader: false,
                modal: true,
                width: '28rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: this.personal
                    ? { assignmentId: this.assignmentId, maxQuantity: this.totalQuantity }
                    : { itemId: this.itemId, maxQuantity: this.totalQuantity }
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
        const upload: Observable<unknown> = this.personal
            ? this.userInventoryService.uploadIssuePhoto(issue.id, file)
            : this.inventoryService.uploadIssuePhoto(issue.id, file);
        upload.pipe(first()).subscribe(() => {
            input.value = '';
            this.load();
        });
    }

    /** Procedura di erasure: per fotografie che ritraggono persone, documenti o altri dati personali. */
    protected erasePhoto(issue: InventoryIssue, photo: InventoryIssuePhoto): void {
        this.confirmService.confirmDestructive({
            title: 'Rimuovere la fotografia?',
            consequence: `La fotografia «${photo.fileName}» viene tolta dalla segnalazione #${issue.id} e cancellata dall'archivio. Usala per immagini con persone o dati personali: l'operazione non si può annullare.`,
            actionLabel: 'Rimuovi fotografia',
            accept: () =>
                this.inventoryService
                    .eraseIssuePhoto(photo.id)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Fotografia rimossa', 'La segnalazione resta invariata.');
                        this.load();
                    })
        });
    }

    protected photoUrl(id: number): string {
        return this.personal ? this.userInventoryService.issuePhotoUrl(id) : this.inventoryService.issuePhotoUrl(id);
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

    /** Ricarica l'elenco dopo una segnalazione aperta da fuori, ad esempio dall'intestazione della pagina. */
    reload(): void {
        this.load();
    }

    private load(): void {
        const request = this.personal ? this.userInventoryService.getAssignmentIssues(this.assignmentId!) : this.inventoryService.getIssues(this.itemId!);
        this.loading = true;
        request.pipe(first()).subscribe({
            next: (issues) => {
                this.issues = issues;
                this.loading = false;
            },
            error: () => (this.loading = false)
        });
    }
}
