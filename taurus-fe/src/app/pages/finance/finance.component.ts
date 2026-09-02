import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { TableLazyLoadEvent } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { first, forkJoin } from 'rxjs';
import { ImportsModule } from '../../imports';
import { AccountingYear, FinancialAccount, FinancialAttachment, FinancialCategory, FinancialCategoryDirection, FinancialDashboard, FinancialDirection, FinancialEventSummary, FinancialMovement, FinancialTransferRequest } from '../../module';
import { FinanceService, ToastService } from '../../service';

@Component({
    selector: 'app-finance',
    standalone: true,
    imports: [ImportsModule, DialogModule, TabsModule],
    templateUrl: './finance.component.html',
    styleUrl: './finance.component.scss',
    providers: [ConfirmationService]
})
export class FinanceComponent implements OnInit {
    protected dashboard?: FinancialDashboard;
    protected accounts: FinancialAccount[] = [];
    protected categories: FinancialCategory[] = [];
    protected movements: FinancialMovement[] = [];
    protected events: FinancialEventSummary[] = [];
    protected years: AccountingYear[] = [];
    protected attachments: FinancialAttachment[] = [];
    protected totalMovements = 0;
    protected loading = false;
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

    protected accountDialog = false;
    protected categoryDialog = false;
    protected movementDialog = false;
    protected transferDialog = false;
    protected eventBudgetDialog = false;

    protected accountForm: FinancialAccount = this.emptyAccount();
    protected categoryForm: FinancialCategory = this.emptyCategory();
    protected movementForm: FinancialMovement = this.emptyMovement();
    protected transferForm: FinancialTransferRequest = this.emptyTransfer();
    protected movementBookingDate = new Date();
    protected movementValueDate?: Date;
    protected transferBookingDate = new Date();
    protected transferValueDate?: Date;
    protected accountOpeningDate = new Date();
    protected selectedAttachment?: File;
    protected attachmentDescription = '';
    protected budgetEvent?: FinancialEventSummary;
    protected budgetFee = 0;
    protected budgetCosts: Array<{ description: string; amount: number }> = [];

    protected readonly accountTypes = [
        { label: 'Cassa', value: 'CASH' },
        { label: 'Conto corrente', value: 'BANK' }
    ];
    protected readonly directions = [
        { label: 'Entrata', value: 'INCOME' },
        { label: 'Uscita', value: 'EXPENSE' }
    ];
    protected readonly categoryDirections = [...this.directions, { label: 'Entrata e uscita', value: 'BOTH' }];
    protected readonly reconciliationOptions = [
        { label: 'Tutti', value: undefined },
        { label: 'Riconciliati', value: true },
        { label: 'Da verificare', value: false }
    ];

    private movementLazyEvent: TableLazyLoadEvent = { first: 0, rows: 20, sortField: 'bookingDate', sortOrder: -1 };
    private handledRouteAction = false;

    constructor(
        private readonly financeService: FinanceService,
        private readonly toastService: ToastService,
        private readonly confirmationService: ConfirmationService,
        private readonly route: ActivatedRoute
    ) {}

    ngOnInit(): void {
        this.loadReferenceData();
        this.loadMovements(this.movementLazyEvent);
    }

    protected get activeAccounts(): FinancialAccount[] {
        return this.accounts.filter((account) => account.active !== false);
    }

    protected get movementCategories(): FinancialCategory[] {
        return this.categories.filter((category) => category.active !== false && (category.direction === 'BOTH' || category.direction === this.movementForm.direction));
    }

    protected openAccount(account?: FinancialAccount): void {
        this.accountForm = account ? { ...account } : this.emptyAccount();
        this.accountOpeningDate = new Date();
        this.accountDialog = true;
    }

    protected saveAccount(): void {
        if (!this.accountForm.name?.trim()) return;
        if (!this.accountForm.id && this.accountForm.initialBalance) this.accountForm.initialBalanceDate = this.formatDate(this.accountOpeningDate);
        const request = this.accountForm.id ? this.financeService.updateAccount(this.accountForm) : this.financeService.createAccount(this.accountForm);
        request.pipe(first()).subscribe(() => {
            this.accountDialog = false;
            this.toastService.success('Conto salvato', 'Le informazioni del conto sono state aggiornate.');
            this.loadReferenceData();
        });
    }

    protected archiveAccount(account: FinancialAccount): void {
        if (!account.id) return;
        this.confirmationService.confirm({
            header: 'Archivia conto',
            message: `Archiviare “${account.name}”? Lo storico rimarrà consultabile.`,
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Archivia',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () =>
                this.financeService
                    .archiveAccount(account.id!)
                    .pipe(first())
                    .subscribe(() => this.loadReferenceData())
        });
    }

    protected openCategory(category?: FinancialCategory): void {
        this.categoryForm = category ? { ...category } : this.emptyCategory();
        this.categoryDialog = true;
    }

    protected saveCategory(): void {
        if (!this.categoryForm.name?.trim()) return;
        const request = this.categoryForm.id ? this.financeService.updateCategory(this.categoryForm) : this.financeService.createCategory(this.categoryForm);
        request.pipe(first()).subscribe(() => {
            this.categoryDialog = false;
            this.toastService.success('Categoria salvata', 'La categoria economica è disponibile.');
            this.loadReferenceData();
        });
    }

    protected archiveCategory(category: FinancialCategory): void {
        if (!category.id) return;
        this.confirmationService.confirm({
            header: 'Archivia categoria',
            message: `Archiviare “${category.name}”? I movimenti esistenti non verranno modificati.`,
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Archivia',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () =>
                this.financeService
                    .archiveCategory(category.id!)
                    .pipe(first())
                    .subscribe(() => this.loadReferenceData())
        });
    }

    protected openMovement(movement?: FinancialMovement): void {
        this.movementForm = movement ? { ...movement } : this.emptyMovement();
        this.movementBookingDate = this.parseDate(this.movementForm.bookingDate) ?? new Date();
        this.movementValueDate = this.parseDate(this.movementForm.valueDate);
        this.attachments = [];
        this.selectedAttachment = undefined;
        this.attachmentDescription = '';
        if (movement?.id) this.loadAttachments(movement.id);
        this.movementDialog = true;
    }

    protected saveMovement(): void {
        if (!this.movementForm.accountId || !this.movementForm.description?.trim() || !this.movementForm.amount) return;
        const payload: FinancialMovement = {
            ...this.movementForm,
            bookingDate: this.formatDate(this.movementBookingDate),
            valueDate: this.movementValueDate ? this.formatDate(this.movementValueDate) : undefined
        };
        const request = payload.id ? this.financeService.updateMovement(payload) : this.financeService.createMovement(payload);
        request.pipe(first()).subscribe((saved) => {
            this.movementDialog = false;
            this.toastService.success('Movimento salvato', 'Saldi e rendiconti sono stati aggiornati.');
            this.refreshAfterMovement();
            if (this.selectedAttachment && saved.id) this.uploadFile(saved.id, this.selectedAttachment);
        });
    }

    protected deleteMovement(movement: FinancialMovement): void {
        if (!movement.id) return;
        this.confirmationService.confirm({
            header: 'Elimina movimento',
            message: 'Il movimento sarà escluso definitivamente da saldi e rendiconti e non potrà essere ripristinato. Vuoi continuare?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina definitivamente',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
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
        this.transferForm = this.emptyTransfer();
        this.transferBookingDate = new Date();
        this.transferValueDate = undefined;
        this.transferDialog = true;
    }

    protected saveTransfer(): void {
        if (!this.transferForm.sourceAccountId || !this.transferForm.destinationAccountId || !this.transferForm.amount || !this.transferForm.description?.trim()) return;
        this.transferForm.bookingDate = this.formatDate(this.transferBookingDate);
        this.transferForm.valueDate = this.transferValueDate ? this.formatDate(this.transferValueDate) : undefined;
        this.financeService
            .createTransfer(this.transferForm)
            .pipe(first())
            .subscribe(() => {
                this.transferDialog = false;
                this.toastService.success('Trasferimento registrato', 'Entrambi i movimenti sono stati creati.');
                this.refreshAfterMovement();
            });
    }

    protected onMovementLazyLoad(event: TableLazyLoadEvent): void {
        this.loadMovements(event);
    }

    protected applyFilters(): void {
        this.movementLazyEvent = { ...this.movementLazyEvent, first: 0 };
        this.loadMovements(this.movementLazyEvent);
    }

    protected resetFilters(): void {
        this.movementFilters = {};
        this.applyFilters();
    }

    protected exportCashbook(format: 'csv' | 'xlsx' | 'pdf'): void {
        const from = this.movementFilters.from ? this.formatDate(this.movementFilters.from) : undefined;
        const to = this.movementFilters.to ? this.formatDate(this.movementFilters.to) : undefined;
        this.financeService
            .exportCashbook(format, from, to, this.movementFilters.accountId, this.movementFilters.categoryId)
            .pipe(first())
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                const anchor = document.createElement('a');
                anchor.href = url;
                anchor.download = `registro-cassa.${format}`;
                anchor.click();
                URL.revokeObjectURL(url);
            });
    }

    protected onAttachmentSelected(event: Event): void {
        this.selectedAttachment = (event.target as HTMLInputElement).files?.[0];
    }

    protected uploadSelectedAttachment(): void {
        if (!this.movementForm.id || !this.selectedAttachment) return;
        this.uploadFile(this.movementForm.id, this.selectedAttachment);
    }

    protected deleteAttachment(attachment: FinancialAttachment): void {
        this.confirmationService.confirm({
            header: 'Elimina allegato',
            message: `Eliminare “${attachment.fileName}”?`,
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () =>
                this.financeService
                    .deleteAttachment(attachment.id)
                    .pipe(first())
                    .subscribe(() => this.loadAttachments(attachment.movementId))
        });
    }

    protected attachmentUrl(attachment: FinancialAttachment): string {
        return this.financeService.attachmentUrl(attachment.id);
    }

    protected downloadAttachment(attachment: FinancialAttachment): void {
        this.financeService
            .downloadAttachment(attachment.id)
            .pipe(first())
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                const anchor = document.createElement('a');
                anchor.href = url;
                anchor.download = attachment.fileName;
                anchor.click();
                URL.revokeObjectURL(url);
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

    protected openEventBudget(event: FinancialEventSummary): void {
        this.budgetEvent = event;
        this.budgetFee = event.expectedFee;
        this.budgetCosts = event.expectedCostItems.map((cost) => ({ description: cost.description, amount: cost.amount }));
        this.eventBudgetDialog = true;
    }

    protected addBudgetCost(): void {
        this.budgetCosts = [...this.budgetCosts, { description: '', amount: 0 }];
    }

    protected removeBudgetCost(index: number): void {
        this.budgetCosts = this.budgetCosts.filter((_, current) => current !== index);
    }

    protected saveEventBudget(): void {
        if (!this.budgetEvent) return;
        const costs = this.budgetCosts.filter((cost) => cost.description.trim() && cost.amount >= 0);
        this.financeService
            .updateEventBudget(this.budgetEvent.eventId, this.budgetFee, costs)
            .pipe(first())
            .subscribe(() => {
                this.eventBudgetDialog = false;
                this.toastService.success('Preventivo aggiornato', 'Compenso e costi previsti sono stati salvati.');
                this.loadReferenceData();
            });
    }

    private loadReferenceData(): void {
        forkJoin({
            dashboard: this.financeService.getDashboard(),
            accounts: this.financeService.getAccounts(true),
            categories: this.financeService.getCategories(true),
            events: this.financeService.getEvents(0, 100),
            years: this.financeService.getYears()
        })
            .pipe(first())
            .subscribe(({ dashboard, accounts, categories, events, years }) => {
                this.dashboard = dashboard;
                this.accounts = accounts;
                this.categories = categories;
                this.events = events.content;
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
            this.openMovement();
            if (Number.isFinite(eventId) && eventId > 0) this.movementForm.eventId = eventId;
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
                from: this.movementFilters.from ? this.formatDate(this.movementFilters.from) : undefined,
                to: this.movementFilters.to ? this.formatDate(this.movementFilters.to) : undefined,
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

    private loadAttachments(movementId: number): void {
        this.financeService
            .getAttachments(movementId)
            .pipe(first())
            .subscribe((attachments) => (this.attachments = attachments));
    }

    private uploadFile(movementId: number, file: File): void {
        this.financeService
            .uploadAttachment(movementId, file, this.attachmentDescription)
            .pipe(first())
            .subscribe(() => {
                this.selectedAttachment = undefined;
                this.attachmentDescription = '';
                this.toastService.success('Allegato caricato', 'Il documento è stato associato al movimento.');
                if (this.movementDialog) this.loadAttachments(movementId);
            });
    }

    private refreshAfterMovement(): void {
        this.loadReferenceData();
        this.loadMovements(this.movementLazyEvent);
    }

    private emptyAccount(): FinancialAccount {
        return { name: '', accountType: 'CASH', currency: 'EUR', displayOrder: 0, active: true };
    }

    private emptyCategory(): FinancialCategory {
        return { name: '', direction: 'EXPENSE', displayOrder: 0, active: true };
    }

    private emptyMovement(): FinancialMovement {
        return {
            accountId: 0,
            direction: 'EXPENSE',
            bookingDate: this.formatDate(new Date()),
            amount: 0,
            description: ''
        };
    }

    private emptyTransfer(): FinancialTransferRequest {
        return { sourceAccountId: 0, destinationAccountId: 0, bookingDate: this.formatDate(new Date()), amount: 0, description: '' };
    }

    private formatDate(date: Date): string {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    private parseDate(value?: string): Date | undefined {
        if (!value) return undefined;
        const [year, month, day] = value.split('-').map(Number);
        return new Date(year, month - 1, day);
    }
}
