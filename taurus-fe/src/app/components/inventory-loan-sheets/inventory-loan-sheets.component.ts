import { Component, inject, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { finalize, first, forkJoin } from 'rxjs';
import { inventoryConditionLabel, inventoryLoanHolderRoleLabel, inventoryLoanReturnOutcomeLabel, inventoryLoanSheetStatusLabel, inventoryLoanSheetStatusSeverity, inventoryLoanTypeLabel, loanDurationIssueMessage } from '../../constants';
import { InventoryLoanSheetCancelDialogComponent } from '../../dialogs/inventory-loan-sheet-cancel-dialog/inventory-loan-sheet-cancel-dialog.component';
import { InventoryLoanSheetCloseDialogComponent } from '../../dialogs/inventory-loan-sheet-close-dialog/inventory-loan-sheet-close-dialog.component';
import { InventoryLoanSheetIssueDialogComponent } from '../../dialogs/inventory-loan-sheet-issue-dialog/inventory-loan-sheet-issue-dialog.component';
import { LoanSheetSignaturesComponent } from '../loan-sheet-signatures/loan-sheet-signatures.component';
import { ImportsModule } from '../../imports';
import { InventoryLoanSheetCloseRequest, InventoryLoanSheetDetail, InventoryLoanSheetIssueRequest, InventoryLoanSheetProposal, InventoryLoanSheetSummary } from '../../module';
import { ConfirmService, InventoryService, TenantFeatureService, ToastService, withErrorSummary } from '../../service';

@Component({
    selector: 'app-inventory-loan-sheets',
    standalone: true,
    imports: [ImportsModule, LoanSheetSignaturesComponent],
    templateUrl: './inventory-loan-sheets.component.html',
    styleUrl: './inventory-loan-sheets.component.scss',
    providers: [DialogService]
})
export class InventoryLoanSheetsComponent implements OnInit, OnChanges {
    @Input({ required: true }) userIndex!: number;

    protected sheets: InventoryLoanSheetSummary[] = [];
    protected proposal?: InventoryLoanSheetProposal;
    protected detail?: InventoryLoanSheetDetail;
    protected loading = false;
    /** Gli atti dei fogli firmati elettronicamente si gestiscono solo con la firma elettronica attiva per il tenant. */
    protected readonly inventorySignatureEnabled = inject(TenantFeatureService).inventorySignatureEnabled;
    protected readonly statusLabel = inventoryLoanSheetStatusLabel;
    protected readonly statusSeverity = inventoryLoanSheetStatusSeverity;
    protected readonly roleLabel = inventoryLoanHolderRoleLabel;
    protected readonly outcomeLabel = inventoryLoanReturnOutcomeLabel;
    protected readonly loanTypeLabel = inventoryLoanTypeLabel;
    protected readonly conditionLabel = inventoryConditionLabel;
    protected readonly issueMessage = loanDurationIssueMessage;
    protected readonly operationLabels: Record<string, string> = { C: 'Consegna', R: 'Restituzione', S: 'Sostituzione', N: 'Rinnovo' };

    constructor(
        private readonly inventoryService: InventoryService,
        private readonly toastService: ToastService,
        private readonly confirmService: ConfirmService,
        private readonly dialogService: DialogService
    ) {}

    ngOnInit(): void {
        this.load();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (!changes['userIndex']?.firstChange) this.load();
    }

    protected get openSheet(): InventoryLoanSheetSummary | undefined {
        return this.sheets.find((sheet) => sheet.status === 'ISSUED');
    }

    protected get canIssue(): boolean {
        return !this.openSheet && !!this.proposal?.assignments.length;
    }

    protected issue(): void {
        if (!this.proposal) return;
        this.dialogService
            .open(InventoryLoanSheetIssueDialogComponent, {
                header: 'Emetti foglio di affidamento',
                showHeader: false,
                modal: true,
                width: '48rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: { userIndex: this.userIndex, proposal: this.proposal }
            })
            .onClose.pipe(first())
            .subscribe((request?: InventoryLoanSheetIssueRequest) => {
                if (!request) return;
                this.inventoryService
                    .issueLoanSheet(this.userIndex, request, withErrorSummary('Emissione non riuscita'))
                    .pipe(first())
                    .subscribe((detail) => {
                        this.toastService.success(
                            'Foglio emesso',
                            detail.summary.signatureMode === 'ELECTRONIC'
                                ? `Il socio riceve la richiesta di inserire i propri dati e firmare il foglio n. ${detail.summary.sheetNumber}.`
                                : `Il foglio n. ${detail.summary.sheetNumber} è pronto da stampare e far firmare.`
                        );
                        this.detail = detail;
                        this.load();
                    });
            });
    }

    protected link(assignmentId: number): void {
        const sheet = this.openSheet;
        if (!sheet) return;
        this.inventoryService
            .linkLoanSheetAssignment(sheet.id, assignmentId)
            .pipe(first())
            .subscribe((detail) => {
                this.toastService.success('Bene aggiunto al foglio', `Il bene compare nel registro delle variazioni del foglio n. ${sheet.sheetNumber}.`);
                this.detail = detail;
                this.load();
            });
    }

    protected showDetail(sheet: InventoryLoanSheetSummary): void {
        if (this.detail?.summary.id === sheet.id) {
            this.detail = undefined;
            return;
        }
        this.inventoryService
            .getLoanSheet(sheet.id)
            .pipe(first())
            .subscribe((detail) => (this.detail = detail));
    }

    protected download(sheet: InventoryLoanSheetSummary): void {
        this.inventoryService
            .downloadLoanSheet(sheet.id)
            .pipe(first())
            .subscribe((blob) => this.save(blob, `foglio-affidamento-${sheet.sheetNumber.replace('/', '-')}.pdf`));
    }

    protected close(sheet: InventoryLoanSheetSummary): void {
        this.dialogService
            .open(InventoryLoanSheetCloseDialogComponent, {
                header: 'Chiudi foglio di affidamento',
                showHeader: false,
                modal: true,
                width: '36rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: { sheet }
            })
            .onClose.pipe(first())
            .subscribe((request?: InventoryLoanSheetCloseRequest) => {
                if (!request) return;
                this.inventoryService
                    .closeLoanSheet(sheet.id, request)
                    .pipe(first())
                    .subscribe((detail) => {
                        this.toastService.success(
                            'Foglio chiuso',
                            detail.summary.signatureMode === 'ELECTRONIC' ? 'Il verbale di riconsegna è da firmare in Taurus: firmato senza addebiti, il foglio si definisce da solo.' : 'Scarica il foglio e fai firmare il verbale di riconsegna.'
                        );
                        this.detail = detail;
                        this.load();
                    });
            });
    }

    protected settle(sheet: InventoryLoanSheetSummary): void {
        const signedActs =
            sheet.signatureMode === 'ELECTRONIC' ? ' Gli atti firmati elettronicamente restano scaricabili nel fascicolo per un periodo limitato, poi Taurus li elimina: scarica il fascicolo e conservalo negli archivi dell’associazione.' : '';
        this.confirmService.confirmDestructive({
            title: 'Definisci foglio',
            consequence:
                (sheet.returnOutcome === 'CHARGES'
                    ? `Confermi che l’addebito del foglio n. ${sheet.sheetNumber} è stato definito? I dati personali del foglio e i PDF generati verranno rimossi.`
                    : `Confermi che il verbale di riconsegna del foglio n. ${sheet.sheetNumber} è stato firmato? I dati personali del foglio e i PDF generati verranno rimossi.`) + signedActs,
            actionLabel: 'Definisci',
            accept: () =>
                this.inventoryService
                    .settleLoanSheet(sheet.id)
                    .pipe(first())
                    .subscribe((detail) => {
                        this.toastService.success('Foglio definito', 'I dati personali del foglio sono stati rimossi.');
                        this.detail = detail;
                        this.load();
                    })
        });
    }

    protected cancel(sheet: InventoryLoanSheetSummary): void {
        this.dialogService
            .open(InventoryLoanSheetCancelDialogComponent, {
                header: 'Annulla foglio di affidamento',
                showHeader: false,
                modal: true,
                width: '32rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: { sheet }
            })
            .onClose.pipe(first())
            .subscribe((reason?: string) => {
                if (!reason) return;
                this.inventoryService
                    .cancelLoanSheet(sheet.id, reason)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Foglio annullato', 'I beni possono essere inseriti in un nuovo foglio.');
                        this.detail = undefined;
                        this.load();
                    });
            });
    }

    /** An act of the sheet changed: the sheet may have been settled by its signed return report. */
    protected refreshDetail(): void {
        const id = this.detail?.summary.id;
        if (!id) return;
        this.inventoryService
            .getLoanSheet(id)
            .pipe(first())
            .subscribe((detail) => (this.detail = detail));
        this.load();
    }

    private load(): void {
        if (!this.userIndex) return;
        this.loading = true;
        forkJoin({
            sheets: this.inventoryService.getLoanSheets(this.userIndex),
            proposal: this.inventoryService.getLoanSheetProposal(this.userIndex)
        })
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe(({ sheets, proposal }) => {
                this.sheets = sheets;
                this.proposal = proposal;
            });
    }

    private save(blob: Blob, fileName: string): void {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = fileName;
        anchor.click();
        URL.revokeObjectURL(url);
    }
}
