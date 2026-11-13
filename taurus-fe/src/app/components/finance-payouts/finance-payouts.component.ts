import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DialogService } from 'primeng/dynamicdialog';
import { catchError, EMPTY, first, of, Subject, switchMap, timer } from 'rxjs';
import { FinancePayoutSettingDialogComponent, PAYOUT_ROUNDING_STEPS, PayoutSettingDialogData, PayoutSettingDialogResult } from '../../dialogs/finance-payout-setting-dialog/finance-payout-setting-dialog.component';
import { FinancePayoutSettlementDialogComponent, PayoutSettlementDialogData, PayoutSettlementDialogResult } from '../../dialogs/finance-payout-settlement-dialog/finance-payout-settlement-dialog.component';
import { ImportsModule } from '../../imports';
import { FinancialAccount, FinancialCategory, FinancialEventSummary, PayoutDistribution, PayoutEvent, PayoutEventLine, PayoutMember, PayoutMemberLine, PayoutMemberStatus, PayoutMemberSummary, PayoutSetting, PayoutSettlement } from '../../module';
import { ApiErrorService, ConfirmService, PayoutService, toIsoDate, ToastService, withErrorSummary, withHandledErrors } from '../../service';
import { AlertSeverity } from '../inline-alert/inline-alert.component';

export type PayoutView = 'simulation' | 'members' | 'settings';

type AdjustmentMode = 'NONE' | 'FACTOR' | 'FIXED';

type SegmentKey = 'expenses' | 'fixed' | 'association' | 'shares' | 'residual';

/** Un segmento della barra di ripartizione: la larghezza è sulla scala comune alle due barre. */
export interface PayoutSegment {
    key: SegmentKey;
    label: string;
    amount: number;
    width: number;
    /** Percentuale della base dell'evento; assente quando la base è zero. */
    percent: number | null;
    /** La quota di cassa piena, quando il margine l'ha ridotta. */
    full?: number;
}

export interface PayoutBar {
    key: 'planned' | 'actual';
    label: string;
    base: number;
    deficit: number;
    segments: PayoutSegment[];
    /** Posizione della linea della base sulla scala comune, in percentuale. */
    baseLine: number;
    deficitWidth: number;
    empty: boolean;
    emptyMessage: string;
}

/** Uno stato particolare della simulazione: un'informazione da spiegare, non un errore. */
export interface PayoutNotice {
    key: string;
    severity: AlertSeverity;
    heading: string;
    detail: string;
    actionLabel?: string;
    actionLink?: Array<string | number>;
}

interface AdjustmentDraft {
    userId: number;
    mode: AdjustmentMode;
    factor: number | null;
    fixedAmount: number | null;
    reason: string;
}

/** Una quota del socio nella vista di liquidazione, con l'importo che l'operatore sceglie di pagare. */
export interface PayoutSettlementRow {
    line: PayoutMemberLine;
    amount: number | null;
}

const SIMULATION_DELAY_MS = 300;
const NOT_CONFIGURED = 'payout.setting.notConfigured';

const currency = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' });

/** Chiave di idempotenza della liquidazione: un nuovo invio dello stesso dialogo non crea un secondo movimento. */
const newRequestKey = (): string => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
    const hex = Array.from({ length: 32 }, () => Math.floor(Math.random() * 16).toString(16));
    hex[12] = '4';
    hex[16] = ((parseInt(hex[16], 16) & 0x3) | 0x8).toString(16);
    const value = hex.join('');
    return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`;
};

/**
 * Compensi ai presenti (docs/event-payout-shares-spec.md): simulazione di un evento, quote per socio e parametri
 * datati. Ogni importo arriva dal backend: il client non ricalcola quote, la percentuale di ciascun segmento della
 * barra a parte, che è soltanto una proporzione per disegnarla.
 */
@Component({
    selector: 'app-finance-payouts',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './finance-payouts.component.html',
    styleUrl: './finance-payouts.component.scss',
    providers: [DialogService]
})
export class FinancePayoutsComponent implements OnInit {
    @Input() accounts: FinancialAccount[] = [];
    @Input() categories: FinancialCategory[] = [];
    @Input() set events(events: FinancialEventSummary[] | null | undefined) {
        this.eventList = events ?? [];
        this.refreshOptions();
    }
    get events(): FinancialEventSummary[] {
        return this.eventList;
    }
    @Input() initialView?: PayoutView;
    @Input() initialEventId?: number;
    @Input() initialUserId?: number;
    /** Una liquidazione registrata: la pagina Economia aggiorna movimenti e saldi. */
    @Output() settled = new EventEmitter<PayoutSettlement>();
    /** Registrare l'incasso dell'evento: il dialogo del movimento è della pagina Economia. */
    @Output() recordIncome = new EventEmitter<number>();

    protected view: PayoutView = 'simulation';
    protected readonly views: Array<{ label: string; value: PayoutView }> = [
        { label: 'Simulazione', value: 'simulation' },
        { label: 'Quote per socio', value: 'members' },
        { label: 'Parametri', value: 'settings' }
    ];

    // Simulazione
    protected eventId?: number;
    protected simulation?: PayoutEvent;
    protected simulationLoading = false;
    protected notConfiguredMessage?: string;
    protected bars: PayoutBar[] = [];
    protected notices: PayoutNotice[] = [];
    protected previewPercent: number | null = null;
    protected previewStep: number | null = null;
    protected sharePercentInput: number | null = null;
    protected editing?: AdjustmentDraft;
    protected editAttempted = false;
    protected savingAdjustment = false;
    protected readonly adjustmentModes: Array<{ label: string; value: AdjustmentMode }> = [
        { label: 'Quota piena', value: 'NONE' },
        { label: 'Fattore', value: 'FACTOR' },
        { label: 'Fisso', value: 'FIXED' }
    ];

    // Quote per socio
    protected members: PayoutMemberSummary[] = [];
    protected membersLoading = false;
    protected membersLoaded = false;
    protected memberFilters: { from?: Date; to?: Date } = {};
    protected memberStatus: PayoutMemberStatus = 'ALL';
    protected readonly memberStatuses: Array<{ label: string; value: PayoutMemberStatus }> = [
        { label: 'Tutte', value: 'ALL' },
        { label: 'Da liquidare', value: 'DUE' },
        { label: 'Liquidate', value: 'SETTLED' }
    ];
    protected member?: PayoutMember;
    protected memberLoading = false;
    protected rows: PayoutSettlementRow[] = [];
    protected selectedRows: PayoutSettlementRow[] = [];
    protected settling = false;

    // Parametri
    protected settings: PayoutSetting[] = [];
    protected settingsLoading = false;
    protected settingsLoaded = false;

    protected eventOptions: Array<{ eventId: number; eventName: string }> = [];
    protected stepOptions: Array<{ label: string; value: number }> = [];

    private eventList: FinancialEventSummary[] = [];
    private readonly simulate$ = new Subject<number>();
    private readonly destroyRef = inject(DestroyRef);

    constructor(
        private readonly payoutService: PayoutService,
        private readonly toastService: ToastService,
        private readonly confirmService: ConfirmService,
        private readonly dialogService: DialogService,
        private readonly apiErrors: ApiErrorService
    ) {}

    ngOnInit(): void {
        this.refreshOptions();
        this.simulate$
            .pipe(
                switchMap((delay) => (delay > 0 ? timer(delay) : of(0)).pipe(switchMap(() => this.fetchSimulation()))),
                takeUntilDestroyed(this.destroyRef)
            )
            .subscribe((simulation) => this.applySimulation(simulation));

        if (this.initialUserId) {
            this.changeView('members');
            this.openMember(this.initialUserId);
        } else if (this.initialView === 'settings' || this.initialView === 'members') {
            this.changeView(this.initialView);
        } else if (this.initialEventId) {
            this.selectEvent(this.initialEventId);
        }
    }

    protected changeView(view: PayoutView): void {
        this.view = view;
        if (view === 'members' && !this.membersLoaded) this.loadMembers();
        if (view === 'settings' && !this.settingsLoaded) this.loadSettings();
    }

    // ---- Simulazione ----

    /**
     * Gli eventi del selettore, con quello aperto anche quando non è fra i più recenti, e i passi dell'anteprima, con
     * quello salvato anche quando non è fra i proposti. Sono campi e non getter: una nuova lista a ogni ciclo di change
     * detection cambierebbe l'input dei selettori a ogni controllo.
     */
    private refreshOptions(): void {
        const options = this.eventList.map((event) => ({ eventId: event.eventId, eventName: event.eventName }));
        const open = this.simulation;
        if (open && !options.some((option) => option.eventId === open.eventId)) options.unshift({ eventId: open.eventId, eventName: open.eventName });
        this.eventOptions = options;

        const steps = [...PAYOUT_ROUNDING_STEPS];
        const saved = open?.savedRoundingStep;
        if (saved !== undefined && !steps.includes(saved)) steps.push(saved);
        this.stepOptions = steps.sort((a, b) => a - b).map((value) => ({ label: value === 0 ? 'Nessuno' : `${value.toLocaleString('it-IT')} €`, value }));
    }

    protected get presentCount(): number {
        return this.simulation?.lines.filter((line) => line.present).length ?? 0;
    }

    protected selectEvent(eventId?: number | null): void {
        this.eventId = eventId ?? undefined;
        this.simulation = undefined;
        this.notConfiguredMessage = undefined;
        this.bars = [];
        this.notices = [];
        this.previewPercent = null;
        this.previewStep = null;
        this.sharePercentInput = null;
        this.editing = undefined;
        this.refreshOptions();
        if (this.eventId) this.simulate(0);
    }

    protected onPercentPreview(value: number | null): void {
        this.sharePercentInput = value;
        if (value === null || value === undefined || value < 0 || value > 100) return;
        this.previewPercent = value === this.simulation?.savedSharePercent ? null : value;
        this.simulate(SIMULATION_DELAY_MS);
    }

    protected onStepPreview(value: number | null): void {
        if (value === null || value === undefined) return;
        this.previewStep = value === this.simulation?.savedRoundingStep ? null : value;
        this.simulate(SIMULATION_DELAY_MS);
    }

    protected resetPreview(): void {
        this.previewPercent = null;
        this.previewStep = null;
        this.sharePercentInput = this.simulation?.savedSharePercent ?? null;
        this.simulate(0);
    }

    /** L'anteprima diventa una versione dei parametri soltanto con un salvataggio esplicito. */
    protected savePreviewAsVersion(): void {
        if (!this.simulation) return;
        this.openSetting(undefined, { associationSharePercent: this.simulation.sharePercent, roundingStep: this.simulation.roundingStep });
    }

    protected startEdit(line: PayoutEventLine): void {
        this.editing = {
            userId: line.userId,
            mode: line.kind === 'FIXED' ? 'FIXED' : line.factor !== null && line.factor !== undefined ? 'FACTOR' : 'NONE',
            factor: line.factor ?? null,
            fixedAmount: line.fixedAmount ?? null,
            reason: line.reason ?? ''
        };
        this.editAttempted = false;
    }

    protected cancelEdit(): void {
        this.editing = undefined;
        this.editAttempted = false;
    }

    protected get factorError(): string | undefined {
        if (this.editing?.mode !== 'FACTOR') return undefined;
        const factor = this.editing.factor;
        if (factor === null || factor === undefined) return 'Indica il fattore.';
        return factor < 0 || factor > 1 ? 'Il fattore è compreso fra 0 e 1.' : undefined;
    }

    protected get fixedAmountError(): string | undefined {
        if (this.editing?.mode !== 'FIXED') return undefined;
        const amount = this.editing.fixedAmount;
        return amount === null || amount === undefined || amount <= 0 ? 'Indica un importo maggiore di zero.' : undefined;
    }

    protected get reasonError(): string | undefined {
        if (!this.editing || this.editing.mode === 'NONE') return undefined;
        return this.editing.reason.trim() ? undefined : 'Il motivo è obbligatorio.';
    }

    protected saveEdit(line: PayoutEventLine): void {
        const draft = this.editing;
        if (!draft || !this.eventId || this.savingAdjustment) return;
        this.editAttempted = true;
        if (this.factorError || this.fixedAmountError || this.reasonError) return;

        const hadAdjustment = line.kind === 'FIXED' || (line.factor !== null && line.factor !== undefined);
        if (draft.mode === 'NONE' && !hadAdjustment) {
            this.cancelEdit();
            return;
        }

        const request =
            draft.mode === 'NONE'
                ? this.payoutService.removeAdjustment(this.eventId, line.userId, withErrorSummary('Modifica non salvata'))
                : this.payoutService.setAdjustment(
                      this.eventId,
                      line.userId,
                      {
                          factor: draft.mode === 'FACTOR' ? draft.factor : null,
                          fixedAmount: draft.mode === 'FIXED' ? draft.fixedAmount : null,
                          reason: draft.reason.trim()
                      },
                      withErrorSummary('Modifica non salvata')
                  );
        this.savingAdjustment = true;
        request.pipe(first()).subscribe({
            next: () => {
                this.savingAdjustment = false;
                this.editing = undefined;
                this.toastService.success(this.adjustmentSummary(draft.mode, line), `${line.name}: la ripartizione è stata ricalcolata.`);
                this.simulate(0);
            },
            error: () => (this.savingAdjustment = false)
        });
    }

    protected onNoticeAction(notice: PayoutNotice): void {
        if (notice.key === 'incomeMissing' && this.eventId) this.recordIncome.emit(this.eventId);
    }

    protected openEventMember(userId: number): void {
        this.changeView('members');
        this.openMember(userId);
    }

    private simulate(delay: number): void {
        this.simulationLoading = true;
        this.simulate$.next(delay);
    }

    private fetchSimulation() {
        if (!this.eventId) return EMPTY;
        return this.payoutService.getEvent(this.eventId, { sharePercent: this.previewPercent, roundingStep: this.previewStep }, withHandledErrors([NOT_CONFIGURED])).pipe(
            catchError((error: HttpErrorResponse) => {
                this.simulationLoading = false;
                const message = this.apiErrors.handledMessage(error, [NOT_CONFIGURED]);
                if (message) {
                    this.simulation = undefined;
                    this.bars = [];
                    this.notices = [];
                    this.notConfiguredMessage = message;
                }
                return EMPTY;
            })
        );
    }

    private applySimulation(simulation: PayoutEvent): void {
        this.simulationLoading = false;
        this.notConfiguredMessage = undefined;
        this.simulation = simulation;
        this.refreshOptions();
        if (this.previewPercent === null) this.sharePercentInput = simulation.savedSharePercent;
        this.bars = this.buildBars(simulation);
        this.notices = this.buildNotices(simulation);
    }

    /** Le due barre sulla stessa scala: l'effettiva è più corta finché l'incasso non è completo. */
    protected buildBars(simulation: PayoutEvent): PayoutBar[] {
        const figures: Array<[PayoutBar['key'], string, PayoutDistribution, string]> = [
            ['planned', 'Previsto', simulation.planned, 'Nessun preventivo: l’evento non ha né compenso né costi previsti.'],
            ['actual', 'Effettivo', simulation.actual, 'Nessun incasso né uscita registrati sull’evento.']
        ];
        const scale = Math.max(0, ...figures.map(([, , distribution]) => distribution.base + distribution.deficit));
        const share = (amount: number) => (scale > 0 ? (amount / scale) * 100 : 0);

        return figures.map(([key, label, distribution, emptyMessage]) => {
            const segments: Array<Omit<PayoutSegment, 'width' | 'percent'>> = [
                { key: 'expenses', label: 'Spese', amount: distribution.expenses },
                { key: 'fixed', label: 'Pagamenti fissi', amount: distribution.fixedPayments },
                {
                    key: 'association',
                    label: 'Quota di cassa',
                    amount: distribution.associationShare,
                    full: distribution.associationShareReduced ? distribution.fullAssociationShare : undefined
                },
                { key: 'shares', label: 'Totale quote', amount: distribution.sharesTotal },
                { key: 'residual', label: 'Residuo', amount: distribution.residual }
            ];
            return {
                key,
                label,
                base: distribution.base,
                deficit: distribution.deficit,
                segments: segments.map((segment) => ({
                    ...segment,
                    width: share(segment.amount),
                    percent: distribution.base > 0 ? (segment.amount / distribution.base) * 100 : null
                })),
                baseLine: share(distribution.base),
                deficitWidth: share(distribution.deficit),
                empty: distribution.base + distribution.deficit <= 0,
                emptyMessage
            };
        });
    }

    /** Gli stati particolari, ciascuno con la spiegazione e, quando c'è, l'azione sensata. */
    protected buildNotices(simulation: PayoutEvent): PayoutNotice[] {
        const notices: PayoutNotice[] = [];
        const distribution = simulation.incomeRecorded ? simulation.actual : simulation.planned;
        const present = simulation.lines.filter((line) => line.present);

        if (!present.length) {
            notices.push({
                key: 'noParticipants',
                severity: 'info',
                heading: 'Nessun presente registrato',
                detail: 'Senza presenze non ci sono quote da ripartire: registra le presenze nella pagina dell’evento.',
                actionLabel: 'Apri l’evento',
                actionLink: ['/calendar', simulation.eventId]
            });
        }
        if (!simulation.incomeRecorded) {
            notices.push({
                key: 'incomeMissing',
                severity: 'info',
                heading: 'Incasso non ancora registrato',
                detail: 'La quota reale è zero finché l’incasso dell’evento non è registrato: oggi sono liquidabili soltanto i pagamenti fissi. La simulazione sul preventivo resta a confronto.',
                actionLabel: 'Registra incasso'
            });
        }
        if (simulation.planned.base <= 0 && simulation.planned.fixedPayments > 0) {
            notices.push({
                key: 'noFee',
                severity: 'info',
                heading: 'Evento senza compenso',
                detail: `I pagamenti fissi sono dovuti per intero (${currency.format(simulation.planned.fixedPayments)}) e restano a carico della cassa insieme alle spese.`
            });
        } else if (distribution.deficit > 0) {
            notices.push({
                key: 'deficit',
                severity: 'warn',
                heading: 'Disavanzo a carico della cassa',
                detail: `Spese e pagamenti fissi superano la base di ${currency.format(distribution.deficit)}: la cassa copre la differenza e non ci sono quote da dividere.`
            });
        } else if (distribution.associationShareReduced) {
            notices.push({
                key: 'reducedShare',
                severity: 'warn',
                heading: 'Quota di cassa ridotta',
                detail: `Il margine non copre la quota di cassa piena di ${currency.format(distribution.fullAssociationShare)}: la cassa trattiene ${currency.format(distribution.associationShare)} e ai presenti a quota non resta nulla da dividere.`
            });
        } else if (present.length && distribution.base > 0 && distribution.net <= 0 && distribution.sharingParticipants > 0) {
            notices.push({
                key: 'noNet',
                severity: 'info',
                heading: 'Netto non sufficiente',
                detail: 'Dopo spese, pagamenti fissi e quota di cassa non resta nulla da dividere fra i presenti a quota.'
            });
        }

        const zeroed = simulation.lines.filter((line) => {
            const theoretical = simulation.incomeRecorded ? line.realTheoreticalShare : line.simulatedTheoreticalShare;
            const amount = simulation.incomeRecorded ? line.realShare : line.simulatedShare;
            return line.kind === 'SHARE' && (theoretical ?? 0) > 0 && amount === 0;
        });
        if (zeroed.length) {
            notices.push({
                key: 'belowStep',
                severity: 'info',
                heading: 'Quote azzerate dal passo',
                detail: `${zeroed.length === 1 ? 'Una quota teorica è inferiore' : `${zeroed.length} quote teoriche sono inferiori`} al passo di ${currency.format(simulation.roundingStep)}: chi la riceverebbe non ottiene nulla e l’importo resta nel residuo.`
            });
        }
        if (simulation.deviationTotal > 0) {
            notices.push({
                key: 'deviation',
                severity: 'warn',
                heading: 'Scostamento da quanto liquidato',
                detail: `Il calcolo attuale è inferiore di ${currency.format(simulation.deviationTotal)} a quanto già pagato: parametri, incassi, spese o pagamenti fissi sono cambiati dopo la liquidazione. Nessun conguaglio è automatico.`
            });
        }
        return notices;
    }

    private adjustmentSummary(mode: AdjustmentMode, line: PayoutEventLine): string {
        if (mode === 'FACTOR') return 'Fattore assegnato';
        if (mode === 'FIXED') return 'Pagamento fisso assegnato';
        return line.kind === 'FIXED' ? 'Pagamento fisso rimosso' : 'Fattore rimosso';
    }

    // ---- Quote per socio ----

    protected loadMembers(): void {
        this.membersLoading = true;
        this.payoutService
            .getMembers(this.memberPeriod())
            .pipe(first())
            .subscribe({
                next: (members) => {
                    this.members = members;
                    this.membersLoading = false;
                    this.membersLoaded = true;
                },
                error: () => (this.membersLoading = false)
            });
    }

    protected resetMemberFilters(): void {
        this.memberFilters = {};
        this.loadMembers();
        if (this.member) this.openMember(this.member.userId);
    }

    protected applyMemberFilters(): void {
        this.loadMembers();
        if (this.member) this.openMember(this.member.userId);
    }

    protected openMember(userId: number): void {
        this.memberLoading = true;
        this.payoutService
            .getMember(userId, { ...this.memberPeriod(), status: this.memberStatus })
            .pipe(first())
            .subscribe({
                next: (member) => {
                    this.member = member;
                    this.rows = member.lines.map((line) => ({ line, amount: line.due > 0 ? line.due : null }));
                    this.selectedRows = [];
                    this.memberLoading = false;
                },
                error: () => (this.memberLoading = false)
            });
    }

    protected closeMember(): void {
        this.member = undefined;
        this.rows = [];
        this.selectedRows = [];
    }

    protected onMemberStatusChange(): void {
        if (this.member) this.openMember(this.member.userId);
    }

    protected selectAllDue(): void {
        this.selectedRows = this.rows.filter((row) => row.line.due > 0);
    }

    /** Si liquida soltanto ciò che resta dovuto. */
    protected readonly isSelectable = ({ data }: { data: PayoutSettlementRow }): boolean => data.line.due > 0;

    protected amountError(row: PayoutSettlementRow): string | undefined {
        const amount = row.amount;
        if (amount === null || amount === undefined || amount <= 0) return 'Indica un importo maggiore di zero.';
        return amount > row.line.due ? `Non oltre il dovuto di ${currency.format(row.line.due)}.` : undefined;
    }

    protected get selectedTotal(): number {
        const cents = this.selectedRows.reduce((total, row) => total + Math.round((row.amount ?? 0) * 100), 0);
        return cents / 100;
    }

    protected get selectionInvalid(): boolean {
        return !this.selectedRows.length || this.selectedRows.some((row) => this.amountError(row));
    }

    protected lineStatus(line: PayoutMemberLine): { label: string; severity: 'success' | 'info' | 'warn' | 'danger' | 'secondary' } {
        if (line.deviation > 0) return { label: 'Scostamento', severity: 'danger' };
        if (line.due > 0 && line.settled > 0) return { label: 'Parziale', severity: 'warn' };
        if (line.due > 0) return { label: 'Da liquidare', severity: 'info' };
        if (line.settled > 0) return { label: 'Liquidata', severity: 'success' };
        if (!line.configured) return { label: 'Parametri mancanti', severity: 'secondary' };
        if (line.kind === 'SHARE' && !line.incomeRecorded) return { label: 'In attesa d’incasso', severity: 'secondary' };
        return { label: 'Nessuna quota', severity: 'secondary' };
    }

    protected openSettlement(): void {
        const member = this.member;
        if (!member || this.selectionInvalid || this.settling) return;
        const rows = [...this.selectedRows];
        const data: PayoutSettlementDialogData = {
            memberName: member.name,
            lines: rows.map((row) => ({ eventName: row.line.eventName, eventDate: row.line.eventDate, amount: row.amount ?? 0 })),
            total: this.selectedTotal,
            accounts: this.accounts,
            categories: this.categories
        };
        const ref = this.dialogService.open(FinancePayoutSettlementDialogComponent, {
            inputValues: data,
            closable: false,
            modal: true,
            showHeader: false,
            width: '40rem',
            breakpoints: { '960px': '75vw', '640px': '94vw' }
        });
        ref?.onClose.pipe(first()).subscribe((result?: PayoutSettlementDialogResult) => {
            if (result) this.settle(member, rows, result);
        });
    }

    private settle(member: PayoutMember, rows: PayoutSettlementRow[], result: PayoutSettlementDialogResult): void {
        this.settling = true;
        this.payoutService
            .settle(
                {
                    ...result,
                    requestKey: newRequestKey(),
                    lines: rows.map((row) => ({ eventId: row.line.eventId, userId: member.userId, amount: row.amount ?? 0 }))
                },
                withErrorSummary('Liquidazione non registrata')
            )
            .pipe(first())
            .subscribe({
                next: (settlement) => {
                    this.settling = false;
                    const events = settlement.eventCount === 1 ? 'un evento' : `${settlement.eventCount} eventi`;
                    this.toastService.success('Movimento generato', `Liquidati ${currency.format(settlement.total)} a ${settlement.userName} per ${events}.`);
                    this.settled.emit(settlement);
                    this.openMember(member.userId);
                    this.loadMembers();
                    if (this.eventId && settlement.lines.some((line) => line.eventId === this.eventId)) this.simulate(0);
                },
                error: () => (this.settling = false)
            });
    }

    private memberPeriod(): { from?: string; to?: string } {
        return {
            from: this.memberFilters.from ? toIsoDate(this.memberFilters.from) : undefined,
            to: this.memberFilters.to ? toIsoDate(this.memberFilters.to) : undefined
        };
    }

    // ---- Parametri ----

    protected loadSettings(): void {
        this.settingsLoading = true;
        this.payoutService
            .getSettings()
            .pipe(first())
            .subscribe({
                next: (settings) => {
                    this.settings = [...settings].sort((a, b) => b.validFrom.localeCompare(a.validFrom));
                    this.settingsLoading = false;
                    this.settingsLoaded = true;
                },
                error: () => (this.settingsLoading = false)
            });
    }

    protected openSetting(setting?: PayoutSetting, proposal?: PayoutSettingDialogData['proposal']): void {
        const ref = this.dialogService.open(FinancePayoutSettingDialogComponent, {
            inputValues: { setting: setting ? { ...setting } : undefined, proposal } satisfies PayoutSettingDialogData,
            closable: false,
            modal: true,
            showHeader: false,
            width: '36rem',
            breakpoints: { '960px': '75vw', '640px': '94vw' }
        });
        ref?.onClose.pipe(first()).subscribe((result?: PayoutSettingDialogResult) => {
            if (result) this.saveSetting(result);
        });
    }

    protected deleteSetting(setting: PayoutSetting): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina versione',
            consequence: `La versione con decorrenza ${this.displayDate(setting.validFrom)} verrà eliminata: gli eventi del suo periodo useranno la versione precedente.`,
            actionLabel: 'Elimina',
            accept: () =>
                this.payoutService
                    .deleteSetting(setting.id, withErrorSummary('Versione non eliminata'))
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Versione eliminata', 'Le simulazioni usano ora le versioni rimaste.');
                        this.afterSettingsChange();
                    })
        });
    }

    private saveSetting(result: PayoutSettingDialogResult): void {
        const request = result.id ? this.payoutService.updateSetting(result.id, result.request, withErrorSummary('Parametri non salvati')) : this.payoutService.createSetting(result.request, withErrorSummary('Parametri non salvati'));
        request.pipe(first()).subscribe(() => {
            this.toastService.success('Parametri salvati', `Valgono dal ${this.displayDate(result.request.validFrom)}.`);
            this.afterSettingsChange();
        });
    }

    private afterSettingsChange(): void {
        this.loadSettings();
        if (this.eventId) {
            this.previewPercent = null;
            this.previewStep = null;
            this.simulate(0);
        }
    }

    private displayDate(value: string): string {
        const [year, month, day] = value.split('-');
        return `${day}/${month}/${year}`;
    }
}
