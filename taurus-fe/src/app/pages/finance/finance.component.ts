import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TableLazyLoadEvent } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { first, forkJoin } from 'rxjs';
import { AccountDialogResult, FinanceAccountDialogComponent } from '../../dialogs/finance-account-dialog/finance-account-dialog.component';
import { FinanceCategoryDialogComponent } from '../../dialogs/finance-category-dialog/finance-category-dialog.component';
import { EventBudgetResult, FinanceEventBudgetDialogComponent } from '../../dialogs/finance-event-budget-dialog/finance-event-budget-dialog.component';
import { FinanceMovementDialogComponent, MovementDialogResult } from '../../dialogs/finance-movement-dialog/finance-movement-dialog.component';
import { FinanceTransferDialogComponent } from '../../dialogs/finance-transfer-dialog/finance-transfer-dialog.component';
import { ImportsModule } from '../../imports';
import {
    AccountingYear,
    AccountingYearSummary,
    EventEconomicStatus,
    FinancialAccount,
    FinancialAccountStatement,
    FinancialCategory,
    FinancialCategoryDirection,
    FinancialDashboard,
    FinancialDirection,
    FinancialEventStatement,
    FinancialEventSummary,
    FinancialMovement,
    FinancialMovementNature,
    FinancialTransferRequest
} from '../../module';
import { ConfirmService, FinanceService, MovementFilters, ReportFormat, ToastService, toIsoDate } from '../../service';

/** Chiave di idempotenza del salvataggio: un retry non deve creare un secondo movimento. */
const newRequestKey = (): string =>
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`;

@Component({
    selector: 'app-finance',
    standalone: true,
    imports: [ImportsModule, TabsModule],
    templateUrl: './finance.component.html',
    styleUrl: './finance.component.scss',
    providers: [DialogService]
})
export class FinanceComponent implements OnInit {
    protected dashboard?: FinancialDashboard;
    protected accounts: FinancialAccount[] = [];
    protected categories: FinancialCategory[] = [];
    protected movements: FinancialMovement[] = [];
    protected events: FinancialEventSummary[] = [];
    protected eventOptions: FinancialEventSummary[] = [];
    protected years: AccountingYear[] = [];
    protected totalMovements = 0;
    protected totalEvents = 0;
    protected loading = false;
    protected eventsLoading = false;
    protected activeTab = 'movements';

    protected movementFilters: {
        from?: Date;
        to?: Date;
        year?: number;
        accountId?: number;
        categoryId?: number;
        eventId?: number;
        direction?: FinancialDirection;
        nature?: FinancialMovementNature;
        counterparty?: string;
        reconciled?: boolean;
        query?: string;
    } = {};

    /** Periodo della sezione Economia: la dashboard non è vincolata all'anno in corso. */
    protected dashboardFrom: Date = new Date(new Date().getFullYear(), 0, 1);
    protected dashboardTo: Date = new Date();

    protected eventFilters: { from?: Date; to?: Date; status?: EventEconomicStatus; query?: string } = {};
    protected eventStatement?: FinancialEventStatement;
    protected eventStatementLoading = false;

    /** Estratto conto e riepilogo esercizio: pannelli di sola lettura aperti dalle rispettive tabelle. */
    protected statement?: FinancialAccountStatement;
    protected statementAccount?: FinancialAccount;
    protected statementLoading = false;
    protected statementFrom: Date = new Date(new Date().getFullYear(), 0, 1);
    protected statementTo: Date = new Date();

    protected yearSummary?: AccountingYearSummary;
    protected yearSummaryLoading = false;

    protected readonly directions = [
        { label: 'Entrata', value: 'INCOME' },
        { label: 'Uscita', value: 'EXPENSE' }
    ];
    protected readonly reconciliationOptions = [
        { label: 'Tutti', value: undefined },
        { label: 'Riconciliati', value: true },
        { label: 'Da verificare', value: false }
    ];
    protected readonly natures = [
        { label: 'Ordinario', value: 'ORDINARY' },
        { label: 'Trasferimento', value: 'TRANSFER' },
        { label: 'Apertura', value: 'OPENING' }
    ];
    protected readonly economicStatuses = [
        { label: 'Nessun preventivo', value: 'NO_BUDGET' },
        { label: 'Movimenti non preventivati', value: 'UNPLANNED_MOVEMENTS' },
        { label: 'Nessun movimento', value: 'NO_MOVEMENTS' },
        { label: 'Preventivo superato', value: 'OVERPAID_OR_OVERRUN' },
        { label: 'Parzialmente saldato', value: 'PARTIALLY_SETTLED' },
        { label: 'Saldato', value: 'SETTLED' }
    ];

    private movementLazyEvent: TableLazyLoadEvent = { first: 0, rows: 20, sortField: 'bookingDate', sortOrder: -1 };
    private eventLazyEvent: TableLazyLoadEvent = { first: 0, rows: 12, sortField: 'startDate', sortOrder: -1 };
    private handledRouteAction = false;

    constructor(
        private readonly financeService: FinanceService,
        private readonly toastService: ToastService,
        private readonly confirmService: ConfirmService,
        private readonly dialogService: DialogService,
        private readonly route: ActivatedRoute
    ) { }

    ngOnInit(): void {
        const params = this.route.snapshot.queryParamMap;
        if (params.get('section') === 'movements') this.activeTab = 'movements';
        if (params.get('reconciled') === 'false') this.movementFilters.reconciled = false;
        this.loadReferenceData();
        this.loadMovements(this.movementLazyEvent);
    }

    protected openAccount(account?: FinancialAccount): void {
        const ref: DynamicDialogRef = this.dialogService.open(FinanceAccountDialogComponent, {
            inputValues: { account: account ? { ...account } : undefined },
            closable: false,
            modal: true,
            showHeader: false,
            width: '40rem',
            breakpoints: { '960px': '75vw', '640px': '94vw' }
        });

        ref.onClose.pipe(first()).subscribe((result?: AccountDialogResult) => {
            if (!result) return;
            const payload = result.account;
            const request = payload.id ? this.financeService.updateAccount(payload) : this.financeService.createAccount(payload);
            request.pipe(first()).subscribe((saved) => {
                this.toastService.success('Conto salvato', 'Le informazioni del conto sono state aggiornate.');
                if (result.openingAttachment && saved.initialBalanceMovementId) {
                    this.uploadFile(saved.initialBalanceMovementId, result.openingAttachment, result.openingAttachmentDescription);
                }
                this.loadReferenceData();
            });
        });
    }

    protected archiveAccount(account: FinancialAccount): void {
        if (!account.id) return;
        this.confirmService.confirmReversible({
            title: 'Archivia conto',
            consequence: `“${account.name}” verrà archiviato; lo storico rimarrà consultabile.`,
            actionLabel: 'Archivia',
            accept: () =>
                this.financeService
                    .archiveAccount(account.id!)
                    .pipe(first())
                    .subscribe(() => this.loadReferenceData())
        });
    }

    protected restoreAccount(account: FinancialAccount): void {
        if (!account.id) return;
        this.financeService
            .restoreAccount(account.id)
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Conto riattivato', `“${account.name}” accetta di nuovo movimenti.`);
                this.loadReferenceData();
            });
    }

    protected deleteAccount(account: FinancialAccount): void {
        if (!account.id) return;
        this.confirmService.confirmDestructive({
            title: 'Elimina conto',
            consequence: `“${account.name}” verrà eliminato definitivamente; non avendo movimenti attivi non resta alcuno storico consultabile.`,
            actionLabel: 'Elimina',
            accept: () =>
                this.financeService
                    .deleteAccount(account.id!)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Conto eliminato', 'Il conto non è più disponibile.');
                        this.loadReferenceData();
                    })
        });
    }

    protected openCategory(category?: FinancialCategory): void {
        const ref: DynamicDialogRef = this.dialogService.open(FinanceCategoryDialogComponent, {
            inputValues: { category: category ? { ...category } : undefined },
            closable: false,
            modal: true,
            showHeader: false,
            width: '40rem',
            breakpoints: { '960px': '75vw', '640px': '94vw' }
        });

        ref.onClose.pipe(first()).subscribe((result?: FinancialCategory) => {
            if (!result) return;
            const request = result.id ? this.financeService.updateCategory(result) : this.financeService.createCategory(result);
            request.pipe(first()).subscribe(() => {
                this.toastService.success('Categoria salvata', 'La categoria economica è disponibile.');
                this.loadReferenceData();
            });
        });
    }

    protected archiveCategory(category: FinancialCategory): void {
        if (!category.id) return;
        this.confirmService.confirmReversible({
            title: 'Archivia categoria',
            consequence: `“${category.name}” verrà archiviata; i movimenti esistenti non saranno modificati.`,
            actionLabel: 'Archivia',
            accept: () =>
                this.financeService
                    .archiveCategory(category.id!)
                    .pipe(first())
                    .subscribe(() => this.loadReferenceData())
        });
    }

    protected openMovement(movement?: FinancialMovement, eventId?: number, direction?: FinancialDirection): void {
        const ref: DynamicDialogRef = this.dialogService.open(FinanceMovementDialogComponent, {
            inputValues: {
                movement: movement ? { ...movement } : undefined,
                accounts: this.accounts,
                categories: this.categories,
                events: this.eventOptions,
                eventId,
                direction
            },
            closable: false,
            modal: true,
            showHeader: false,
            width: '56rem',
            contentStyle: { overflow: 'hidden' },
            breakpoints: { '960px': '80vw', '640px': '94vw' }
        });

        ref.onClose.pipe(first()).subscribe((result?: MovementDialogResult) => {
            if (!result) return;
            const warning = this.negativeBalanceWarning(result.movement);
            if (!warning) {
                this.saveMovement(result);
                return;
            }
            // Un saldo negativo non è vietato: in un'associazione capita di anticipare una spesa.
            // Si chiede conferma perché spesso è invece un conto sbagliato o un importo di troppo.
            this.confirmService.confirmReversible({
                title: 'Il conto va sotto zero',
                consequence: warning,
                actionLabel: 'Registra comunque',
                accept: () => this.saveMovement(result)
            });
        });
    }

    /** Il saldo del conto dopo l'uscita, quando diventa negativo. */
    private negativeBalanceWarning(movement: FinancialMovement): string | undefined {
        if (movement.direction !== 'EXPENSE' || movement.id) return undefined;
        const account = this.accounts.find((candidate) => candidate.id === movement.accountId);
        if (!account || account.balance === undefined) return undefined;
        const after = account.balance - movement.amount;
        if (after >= 0) return undefined;
        const formatted = after.toLocaleString('it-IT', { style: 'currency', currency: account.currency || 'EUR' });
        return `Dopo questa uscita “${account.name}” avrebbe un saldo di ${formatted}.`;
    }

    private saveMovement(result: MovementDialogResult): void {
        const payload: FinancialMovement = result.movement.id
            ? result.movement
            : { ...result.movement, requestKey: newRequestKey() };
        const request = payload.id ? this.financeService.updateMovement(payload) : this.financeService.createMovement(payload);
        request.pipe(first()).subscribe((saved) => {
            this.toastService.success('Movimento salvato', 'Saldi e rendiconti sono stati aggiornati.');
            this.noticeRecalculatedOpenings(saved);
            this.refreshAfterMovement();
            if (result.pendingAttachment && saved.id) this.uploadFile(saved.id, result.pendingAttachment, result.attachmentDescription);
        });
    }

    /**
     * Un movimento registrato in un esercizio passato cambia i saldi di apertura di tutti gli anni
     * successivi: il backend li rifà da solo, ma chi salva deve sapere che è accaduto.
     */
    private noticeRecalculatedOpenings(movement: FinancialMovement): void {
        const year = movement.accountingYear;
        if (!year || year >= new Date().getFullYear()) return;
        this.toastService.info(
            'Riporti ricalcolati',
            `Il movimento è nell’esercizio ${year}: i saldi di apertura degli esercizi successivi sono stati aggiornati.`
        );
    }

    protected deleteMovement(movement: FinancialMovement): void {
        if (!movement.id) return;
        this.confirmService.confirmDestructive({
            title: 'Elimina movimento',
            consequence: 'Il movimento sarà escluso definitivamente da saldi e rendiconti e non potrà essere ripristinato.',
            actionLabel: 'Elimina definitivamente',
            accept: () =>
                this.financeService
                    .deleteMovement(movement.id!)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Movimento eliminato', 'I saldi sono stati ricalcolati.');
                        this.refreshAfterMovement();
                    })
        });
    }

    protected toggleReconciliation(movement: FinancialMovement): void {
        if (!movement.id) return;
        this.financeService
            .reconcileMovement(movement.id, !movement.reconciled)
            .pipe(first())
            .subscribe(() => this.refreshAfterMovement());
    }

    protected openTransfer(): void {
        const ref: DynamicDialogRef = this.dialogService.open(FinanceTransferDialogComponent, {
            inputValues: { accounts: this.accounts.filter((account) => account.active !== false) },
            closable: false,
            modal: true,
            showHeader: false,
            width: '40rem',
            breakpoints: { '960px': '75vw', '640px': '94vw' }
        });

        ref.onClose.pipe(first()).subscribe((result?: FinancialTransferRequest) => {
            if (!result) return;
            this.financeService
                .createTransfer({ ...result, requestKey: newRequestKey() })
                .pipe(first())
                .subscribe(() => {
                    this.toastService.success('Trasferimento registrato', 'Entrambi i movimenti sono stati creati.');
                    this.refreshAfterMovement();
                });
        });
    }

    protected openEventBudget(event: FinancialEventSummary): void {
        const ref: DynamicDialogRef = this.dialogService.open(FinanceEventBudgetDialogComponent, {
            inputValues: { event },
            closable: false,
            modal: true,
            showHeader: false,
            width: '40rem',
            breakpoints: { '960px': '75vw', '640px': '94vw' }
        });

        ref.onClose.pipe(first()).subscribe((result?: EventBudgetResult) => {
            if (!result) return;
            this.financeService
                .updateEventBudget(event.eventId, result.fee, result.costs)
                .pipe(first())
                .subscribe(() => {
                    this.toastService.success('Preventivo aggiornato', 'Compenso e costi previsti sono stati salvati.');
                    this.loadReferenceData();
                    this.loadEvents(this.eventLazyEvent);
                });
        });
    }

    protected onMovementLazyLoad(event: TableLazyLoadEvent): void {
        this.loadMovements(event);
    }

    protected onEventLazyLoad(event: TableLazyLoadEvent): void {
        this.loadEvents(event);
    }

    protected applyFilters(): void {
        this.movementLazyEvent = { ...this.movementLazyEvent, first: 0 };
        this.loadMovements(this.movementLazyEvent);
    }

    protected resetFilters(): void {
        this.movementFilters = {};
        this.applyFilters();
    }

    protected applyEventFilters(): void {
        this.eventLazyEvent = { ...this.eventLazyEvent, first: 0 };
        this.loadEvents(this.eventLazyEvent);
    }

    protected resetEventFilters(): void {
        this.eventFilters = {};
        this.applyEventFilters();
    }

    protected reloadDashboard(): void {
        this.financeService
            .getDashboard(toIsoDate(this.dashboardFrom), toIsoDate(this.dashboardTo))
            .pipe(first())
            .subscribe((dashboard) => (this.dashboard = dashboard));
    }

    protected exportCashbook(format: ReportFormat): void {
        this.financeService
            .exportCashbook(format, this.currentMovementFilters())
            .pipe(first())
            .subscribe((blob) => this.download(blob, `registro-cassa.${format}`));
    }

    /** I filtri correnti nel formato delle API: l'esportazione riceve esattamente questi. */
    private currentMovementFilters(): MovementFilters {
        return {
            from: this.movementFilters.from ? toIsoDate(this.movementFilters.from) : undefined,
            to: this.movementFilters.to ? toIsoDate(this.movementFilters.to) : undefined,
            year: this.movementFilters.year,
            accountId: this.movementFilters.accountId,
            categoryId: this.movementFilters.categoryId,
            eventId: this.movementFilters.eventId,
            direction: this.movementFilters.direction,
            nature: this.movementFilters.nature,
            counterparty: this.movementFilters.counterparty,
            reconciled: this.movementFilters.reconciled,
            query: this.movementFilters.query
        };
    }

    /** La pagina dell'evento: preventivo, consuntivo, scostamenti e movimenti collegati. */
    protected showEventStatement(event: FinancialEventSummary): void {
        this.eventStatementLoading = true;
        this.financeService
            .getEventStatement(event.eventId)
            .pipe(first())
            .subscribe({
                next: (statement) => {
                    this.eventStatement = statement;
                    this.eventStatementLoading = false;
                },
                error: () => (this.eventStatementLoading = false)
            });
    }

    protected closeEventStatement(): void {
        this.eventStatement = undefined;
    }

    protected exportEventStatement(format: ReportFormat): void {
        if (!this.eventStatement) return;
        this.financeService
            .exportEventStatement(format, this.eventStatement.summary.eventId)
            .pipe(first())
            .subscribe((blob) => this.download(blob, `rendiconto-evento.${format}`));
    }

    protected registerEventIncome(eventId: number): void {
        this.openMovement(undefined, eventId, 'INCOME');
    }

    protected registerEventExpense(eventId: number): void {
        this.openMovement(undefined, eventId, 'EXPENSE');
    }

    protected filterMovementsByEvent(eventId: number): void {
        this.movementFilters = { ...this.movementFilters, eventId };
        this.activeTab = 'movements';
        this.applyFilters();
    }

    /** Sposta il conto di una posizione e salva l'ordine completo. */
    protected moveAccount(account: FinancialAccount, delta: number): void {
        const ids = this.reordered(this.accounts.map((item) => item.id!), account.id!, delta);
        if (!ids) return;
        this.financeService
            .reorderAccounts(ids)
            .pipe(first())
            .subscribe((accounts) => {
                this.accounts = accounts;
                this.toastService.success('Ordine aggiornato', 'I conti compaiono nel nuovo ordine in tutte le schermate.');
            });
    }

    protected moveCategory(category: FinancialCategory, delta: number): void {
        const ids = this.reordered(this.categories.map((item) => item.id!), category.id!, delta);
        if (!ids) return;
        this.financeService
            .reorderCategories(ids)
            .pipe(first())
            .subscribe((categories) => {
                this.categories = categories;
                this.toastService.success('Ordine aggiornato', 'Le categorie compaiono nel nuovo ordine in tutte le schermate.');
            });
    }

    /** L'elenco con l'elemento spostato di delta posizioni, o undefined se è già al limite. */
    private reordered(ids: number[], id: number, delta: number): number[] | undefined {
        const index = ids.indexOf(id);
        const target = index + delta;
        if (index < 0 || target < 0 || target >= ids.length) return undefined;
        const moved = [...ids];
        moved.splice(target, 0, ...moved.splice(index, 1));
        return moved;
    }

    protected showStatement(account: FinancialAccount): void {
        this.statementAccount = account;
        this.loadStatement();
    }

    protected closeStatement(): void {
        this.statementAccount = undefined;
        this.statement = undefined;
    }

    protected loadStatement(): void {
        if (!this.statementAccount?.id) return;
        this.statementLoading = true;
        this.financeService
            .getAccountStatement(this.statementAccount.id, toIsoDate(this.statementFrom), toIsoDate(this.statementTo))
            .pipe(first())
            .subscribe({
                next: (statement) => {
                    this.statement = statement;
                    this.statementLoading = false;
                },
                error: () => (this.statementLoading = false)
            });
    }

    protected exportStatement(format: ReportFormat): void {
        if (!this.statementAccount?.id) return;
        this.financeService
            .exportAccountStatement(format, this.statementAccount.id, toIsoDate(this.statementFrom), toIsoDate(this.statementTo))
            .pipe(first())
            .subscribe((blob) => this.download(blob, `estratto-conto.${format}`));
    }

    protected exportEventsReport(format: ReportFormat): void {
        this.financeService
            .exportEventsReport(format, toIsoDate(this.statementFrom), toIsoDate(this.statementTo))
            .pipe(first())
            .subscribe((blob) => this.download(blob, `rendiconto-eventi.${format}`));
    }

    protected exportCategoriesReport(format: ReportFormat): void {
        this.financeService
            .exportCategoriesReport(format, toIsoDate(this.statementFrom), toIsoDate(this.statementTo))
            .pipe(first())
            .subscribe((blob) => this.download(blob, `rendiconto-categorie.${format}`));
    }

    protected showYearSummary(year: AccountingYear): void {
        this.yearSummaryLoading = true;
        this.financeService
            .getYearSummary(year.year)
            .pipe(first())
            .subscribe({
                next: (summary) => {
                    this.yearSummary = summary;
                    this.yearSummaryLoading = false;
                },
                error: () => (this.yearSummaryLoading = false)
            });
    }

    protected closeYearSummary(): void {
        this.yearSummary = undefined;
    }

    protected exportAnnualReport(year: number, format: ReportFormat): void {
        this.financeService
            .exportAnnualReport(format, year)
            .pipe(first())
            .subscribe((blob) => this.download(blob, `rendiconto-annuale-${year}.${format}`));
    }

    protected recalculateYear(year: AccountingYear): void {
        this.financeService
            .recalculate(year.year)
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Riporti ricalcolati', `I saldi di apertura successivi al ${year.year} sono stati aggiornati.`);
                this.loadReferenceData();
                if (this.yearSummary?.year.year === year.year) this.showYearSummary(year);
            });
    }

    protected rolloverYear(year: number): void {
        this.confirmService.confirmReversible({
            title: 'Rigenera il riporto',
            consequence: `Le aperture del ${year + 1} saranno riscritte sui saldi attuali del ${year}.`,
            actionLabel: 'Rigenera',
            accept: () =>
                this.financeService
                    .rollover(year)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Riporto aggiornato', `Il saldo al 31/12/${year} è stato riportato all’anno successivo.`);
                        this.loadReferenceData();
                    })
        });
    }

    protected rolloverCurrentYear(): void {
        const year = new Date().getFullYear() - 1;
        this.financeService
            .rollover(year)
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Riporto aggiornato', `Il saldo al 31/12/${year} è stato riportato all’anno successivo.`);
                this.loadReferenceData();
            });
    }

    protected directionLabel(direction: FinancialDirection | FinancialCategoryDirection): string {
        if (direction === 'INCOME') return 'Entrata';
        if (direction === 'EXPENSE') return 'Uscita';
        return 'Entrata e uscita';
    }

    protected eventStatusLabel(status: string): string {
        const labels: Record<string, string> = {
            NO_BUDGET: 'Nessun preventivo',
            UNPLANNED_MOVEMENTS: 'Movimenti non preventivati',
            NO_MOVEMENTS: 'Nessun movimento',
            OVERPAID_OR_OVERRUN: 'Preventivo superato',
            SETTLED: 'Saldato',
            PARTIALLY_SETTLED: 'Parzialmente saldato'
        };
        return labels[status] ?? status;
    }

    protected eventStatusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
        if (status === 'SETTLED') return 'success';
        if (status === 'OVERPAID_OR_OVERRUN' || status === 'UNPLANNED_MOVEMENTS') return 'danger';
        if (status === 'PARTIALLY_SETTLED' || status === 'NO_MOVEMENTS') return 'warn';
        return 'secondary';
    }

    private loadReferenceData(): void {
        forkJoin({
            dashboard: this.financeService.getDashboard(toIsoDate(this.dashboardFrom), toIsoDate(this.dashboardTo)),
            accounts: this.financeService.getAccounts(true),
            categories: this.financeService.getCategories(true),
            eventOptions: this.financeService.getEvents({ page: 0, size: 100 }),
            years: this.financeService.getYears()
        })
            .pipe(first())
            .subscribe(({ dashboard, accounts, categories, eventOptions, years }) => {
                this.dashboard = dashboard;
                this.accounts = accounts;
                this.categories = categories;
                this.eventOptions = eventOptions.content;
                this.years = years;
                this.handleRouteAction();
            });
    }

    private handleRouteAction(): void {
        if (this.handledRouteAction) return;
        this.handledRouteAction = true;
        const params = this.route.snapshot.queryParamMap;
        const tab = params.get('tab');
        if (tab && ['movements', 'accounts', 'categories', 'events', 'years'].includes(tab)) this.activeTab = tab;

        const eventId = Number(params.get('eventId'));
        if (params.get('action') === 'new') {
            this.openMovement(undefined, Number.isFinite(eventId) && eventId > 0 ? eventId : undefined);
            return;
        }

        const movementId = Number(params.get('movementId'));
        if (Number.isFinite(movementId) && movementId > 0) {
            this.financeService
                .getMovement(movementId)
                .pipe(first())
                .subscribe((movement) => this.openMovement(movement));
            return;
        }

        if (Number.isFinite(eventId) && eventId > 0) {
            this.financeService
                .getEvent(eventId)
                .pipe(first())
                .subscribe((event) => this.openEventBudget(event));
        }
    }

    private loadMovements(event: TableLazyLoadEvent): void {
        this.movementLazyEvent = event;
        const rows = event.rows ?? 20;
        const field = Array.isArray(event.sortField) ? event.sortField[0] : event.sortField;
        const sort = `${field ?? 'bookingDate'},${(event.sortOrder ?? -1) > 0 ? 'asc' : 'desc'}`;
        this.loading = true;
        this.financeService
            .getMovements({
                page: Math.floor((event.first ?? 0) / rows),
                size: rows,
                sort,
                ...this.currentMovementFilters()
            })
            .pipe(first())
            .subscribe({
                next: (page) => {
                    this.movements = page.content;
                    this.totalMovements = page.totalElements;
                    this.loading = false;
                },
                error: () => (this.loading = false)
            });
    }

    private loadEvents(event: TableLazyLoadEvent): void {
        this.eventLazyEvent = event;
        const rows = event.rows ?? 12;
        const page = Math.floor((event.first ?? 0) / rows);
        const sortField = typeof event.sortField === 'string' ? event.sortField : 'startDate';
        const sortDirection = event.sortOrder === 1 ? 'asc' : 'desc';
        this.eventsLoading = true;
        this.financeService
            .getEvents({
                page,
                size: rows,
                sort: `${sortField},${sortDirection}`,
                from: this.eventFilters.from ? toIsoDate(this.eventFilters.from) : undefined,
                to: this.eventFilters.to ? toIsoDate(this.eventFilters.to) : undefined,
                status: this.eventFilters.status,
                query: this.eventFilters.query
            })
            .pipe(first())
            .subscribe({
                next: (events) => {
                    this.events = events.content;
                    this.totalEvents = events.totalElements;
                    this.eventsLoading = false;
                },
                error: () => (this.eventsLoading = false)
            });
    }

    private uploadFile(movementId: number, file: File, description?: string): void {
        this.financeService
            .uploadAttachment(movementId, file, description)
            .pipe(first())
            .subscribe(() => this.toastService.success('Allegato caricato', 'Il documento è stato associato al movimento.'));
    }

    private refreshAfterMovement(): void {
        this.loadReferenceData();
        this.loadMovements(this.movementLazyEvent);
        this.loadEvents(this.eventLazyEvent);
        if (this.statementAccount) this.loadStatement();
        if (this.eventStatement) this.showEventStatement(this.eventStatement.summary);
    }

    private download(blob: Blob, fileName: string): void {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = fileName;
        anchor.click();
        URL.revokeObjectURL(url);
    }
}
