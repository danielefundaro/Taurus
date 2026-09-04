import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TableLazyLoadEvent } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { first, forkJoin } from 'rxjs';
import { FinanceAccountDialogComponent } from '../../dialogs/finance-account-dialog/finance-account-dialog.component';
import { FinanceCategoryDialogComponent } from '../../dialogs/finance-category-dialog/finance-category-dialog.component';
import { EventBudgetResult, FinanceEventBudgetDialogComponent } from '../../dialogs/finance-event-budget-dialog/finance-event-budget-dialog.component';
import { FinanceMovementDialogComponent, MovementDialogResult } from '../../dialogs/finance-movement-dialog/finance-movement-dialog.component';
import { FinanceTransferDialogComponent } from '../../dialogs/finance-transfer-dialog/finance-transfer-dialog.component';
import { ImportsModule } from '../../imports';
import {
    AccountingYear,
    AccountingYearSummary,
    FinancialAccount,
    FinancialAccountStatement,
    FinancialCategory,
    FinancialCategoryDirection,
    FinancialDashboard,
    FinancialDirection,
    FinancialEventSummary,
    FinancialMovement,
    FinancialTransferRequest
} from '../../module';
import { ConfirmService, FinanceService, ReportFormat, ToastService, toIsoDate } from '../../service';

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
        accountId?: number;
        categoryId?: number;
        direction?: FinancialDirection;
        reconciled?: boolean;
        query?: string;
    } = {};

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

    private movementLazyEvent: TableLazyLoadEvent = { first: 0, rows: 20, sortField: 'bookingDate', sortOrder: -1 };
    private eventLazyEvent: TableLazyLoadEvent = { first: 0, rows: 12, sortField: 'startDate', sortOrder: -1 };
    private handledRouteAction = false;

    constructor(
        private readonly financeService: FinanceService,
        private readonly toastService: ToastService,
        private readonly confirmService: ConfirmService,
        private readonly dialogService: DialogService,
        private readonly route: ActivatedRoute
    ) {}

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
            closable: true,
            modal: true,
            showHeader: false,
            width: '40rem',
            breakpoints: { '960px': '75vw', '640px': '94vw' }
        });

        ref.onClose.pipe(first()).subscribe((result?: FinancialAccount) => {
            if (!result) return;
            const request = result.id ? this.financeService.updateAccount(result) : this.financeService.createAccount(result);
            request.pipe(first()).subscribe(() => {
                this.toastService.success('Conto salvato', 'Le informazioni del conto sono state aggiornate.');
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

    protected openCategory(category?: FinancialCategory): void {
        const ref: DynamicDialogRef = this.dialogService.open(FinanceCategoryDialogComponent, {
            inputValues: { category: category ? { ...category } : undefined },
            closable: true,
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

    protected openMovement(movement?: FinancialMovement, eventId?: number): void {
        const ref: DynamicDialogRef = this.dialogService.open(FinanceMovementDialogComponent, {
            inputValues: {
                movement: movement ? { ...movement } : undefined,
                accounts: this.accounts,
                categories: this.categories,
                events: this.eventOptions,
                eventId
            },
            closable: true,
            modal: true,
            showHeader: false,
            width: '56rem',
            breakpoints: { '960px': '80vw', '640px': '94vw' }
        });

        ref.onClose.pipe(first()).subscribe((result?: MovementDialogResult) => {
            if (!result) return;
            const payload = result.movement;
            const request = payload.id ? this.financeService.updateMovement(payload) : this.financeService.createMovement(payload);
            request.pipe(first()).subscribe((saved) => {
                this.toastService.success('Movimento salvato', 'Saldi e rendiconti sono stati aggiornati.');
                this.refreshAfterMovement();
                if (result.pendingAttachment && saved.id) this.uploadFile(saved.id, result.pendingAttachment, result.attachmentDescription);
            });
        });
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
            closable: true,
            modal: true,
            showHeader: false,
            width: '40rem',
            breakpoints: { '960px': '75vw', '640px': '94vw' }
        });

        ref.onClose.pipe(first()).subscribe((result?: FinancialTransferRequest) => {
            if (!result) return;
            this.financeService
                .createTransfer(result)
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
            closable: true,
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

    protected exportCashbook(format: ReportFormat): void {
        const from = this.movementFilters.from ? toIsoDate(this.movementFilters.from) : undefined;
        const to = this.movementFilters.to ? toIsoDate(this.movementFilters.to) : undefined;
        this.financeService
            .exportCashbook(format, from, to, this.movementFilters.accountId, this.movementFilters.categoryId)
            .pipe(first())
            .subscribe((blob) => this.download(blob, `registro-cassa.${format}`));
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
            dashboard: this.financeService.getDashboard(),
            accounts: this.financeService.getAccounts(true),
            categories: this.financeService.getCategories(true),
            eventOptions: this.financeService.getEvents(0, 100),
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
                from: this.movementFilters.from ? toIsoDate(this.movementFilters.from) : undefined,
                to: this.movementFilters.to ? toIsoDate(this.movementFilters.to) : undefined,
                accountId: this.movementFilters.accountId,
                categoryId: this.movementFilters.categoryId,
                direction: this.movementFilters.direction,
                reconciled: this.movementFilters.reconciled,
                query: this.movementFilters.query
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
            .getEvents(page, rows, `${sortField},${sortDirection}`)
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
