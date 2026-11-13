import { HttpErrorResponse } from '@angular/common/http';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DialogService } from 'primeng/dynamicdialog';
import { of, throwError } from 'rxjs';
import { FinancePayoutSettingDialogComponent } from '../../dialogs/finance-payout-setting-dialog/finance-payout-setting-dialog.component';
import { FinancePayoutSettlementDialogComponent } from '../../dialogs/finance-payout-settlement-dialog/finance-payout-settlement-dialog.component';
import { FinancialEventSummary, PayoutDistribution, PayoutEvent, PayoutEventLine, PayoutMember, PayoutSettlement } from '../../module';
import { ApiErrorService, ConfirmService, PayoutService, ToastService } from '../../service';
import { FinancePayoutsComponent } from './finance-payouts.component';

type Payouts = jasmine.SpyObj<PayoutService>;

const distribution = (overrides: Partial<PayoutDistribution> = {}): PayoutDistribution => ({
    base: 0,
    expenses: 0,
    fixedPayments: 0,
    margin: 0,
    fullAssociationShare: 0,
    associationShare: 0,
    associationShareReduced: false,
    net: 0,
    baseShare: 0,
    sharesTotal: 0,
    residual: 0,
    deficit: 0,
    effectiveAssociationShare: 0,
    sharingParticipants: 0,
    ...overrides
});

const line = (overrides: Partial<PayoutEventLine>): PayoutEventLine => ({
    userId: 1,
    name: 'Anna Bianchi',
    present: true,
    kind: 'SHARE',
    simulatedShare: 0,
    realShare: 0,
    settled: 0,
    due: 0,
    deviation: 0,
    ...overrides
});

/**
 * Preventivo: base 1000, spese 200, un fisso di 180, cassa al 20% = 200, netto 420 su due presenti a quota con passo 5
 * (210 e, con fattore 0,5, 105), residuo 105. Consuntivo: incasso 800, spese 250, stesso fisso, cassa 160, netto 210
 * (105 e 52,50 arrotondato a 50), residuo 55.
 */
const simulation = (overrides: Partial<PayoutEvent> = {}): PayoutEvent => ({
    eventId: 70,
    eventName: 'Concerto di primavera',
    eventDate: '2026-04-18',
    settingId: 1,
    settingValidFrom: '2026-01-01',
    savedSharePercent: 20,
    savedRoundingStep: 5,
    sharePercent: 20,
    roundingStep: 5,
    preview: false,
    incomeRecorded: true,
    planned: distribution({
        base: 1000,
        expenses: 200,
        fixedPayments: 180,
        margin: 620,
        fullAssociationShare: 200,
        associationShare: 200,
        net: 420,
        baseShare: 210,
        sharesTotal: 315,
        residual: 105,
        effectiveAssociationShare: 305,
        sharingParticipants: 2
    }),
    actual: distribution({
        base: 800,
        expenses: 250,
        fixedPayments: 180,
        margin: 370,
        fullAssociationShare: 160,
        associationShare: 160,
        net: 210,
        baseShare: 105,
        sharesTotal: 155,
        residual: 55,
        effectiveAssociationShare: 215,
        sharingParticipants: 2
    }),
    lines: [
        line({ userId: 1, name: 'Anna Bianchi', simulatedTheoreticalShare: 210, simulatedShare: 210, realTheoreticalShare: 105, realShare: 105, due: 105 }),
        line({
            userId: 2,
            name: 'Bruno Verdi',
            factor: 0.5,
            reason: 'Metà serata',
            simulatedTheoreticalShare: 105,
            simulatedShare: 105,
            realTheoreticalShare: 52.5,
            realShare: 50,
            due: 50
        }),
        line({ userId: 3, name: 'Carla Neri', kind: 'FIXED', fixedAmount: 180, reason: 'Ingaggio', simulatedShare: 180, realShare: 180, settled: 180 })
    ],
    settledTotal: 180,
    dueTotal: 155,
    deviationTotal: 0,
    ...overrides
});

const member: PayoutMember = {
    userId: 1,
    name: 'Anna Bianchi',
    dueTotal: 155,
    settledTotal: 100,
    deviationTotal: 0,
    lines: [
        {
            eventId: 70,
            eventName: 'Concerto di primavera',
            eventDate: '2026-04-18',
            present: true,
            configured: true,
            incomeRecorded: true,
            kind: 'SHARE',
            simulatedShare: 210,
            realShare: 105,
            settled: 0,
            due: 105,
            deviation: 0
        },
        {
            eventId: 71,
            eventName: 'Serata benefica',
            eventDate: '2026-05-02',
            present: true,
            configured: true,
            incomeRecorded: false,
            kind: 'FIXED',
            fixedAmount: 150,
            reason: 'Direzione',
            simulatedShare: 150,
            realShare: 150,
            settled: 100,
            due: 50,
            deviation: 0
        },
        {
            eventId: 72,
            eventName: 'Prova aperta',
            eventDate: '2026-05-20',
            present: true,
            configured: true,
            incomeRecorded: true,
            kind: 'SHARE',
            simulatedShare: 40,
            realShare: 40,
            settled: 40,
            due: 0,
            deviation: 0
        }
    ]
};

describe('FinancePayoutsComponent', () => {
    function create(dialogResult?: unknown): {
        component: FinancePayoutsComponent;
        payoutService: Payouts;
        toastService: jasmine.SpyObj<ToastService>;
        confirmService: jasmine.SpyObj<ConfirmService>;
        dialogService: { open: jasmine.Spy };
        apiErrors: jasmine.SpyObj<ApiErrorService>;
    } {
        const payoutService = jasmine.createSpyObj<PayoutService>('PayoutService', [
            'getSettings',
            'createSetting',
            'updateSetting',
            'deleteSetting',
            'getAffectedEvents',
            'getEvent',
            'setAdjustment',
            'removeAdjustment',
            'getMembers',
            'getMember',
            'settle'
        ]);
        payoutService.getEvent.and.returnValue(of(simulation()));
        payoutService.getSettings.and.returnValue(of([]));
        payoutService.getMembers.and.returnValue(of([]));
        payoutService.getMember.and.returnValue(of(member));
        payoutService.setAdjustment.and.returnValue(of(undefined));
        payoutService.removeAdjustment.and.returnValue(of(undefined));
        payoutService.createSetting.and.callFake((request) => of({ id: 5, ...request }));
        payoutService.updateSetting.and.callFake((id, request) => of({ id, ...request }));
        payoutService.deleteSetting.and.returnValue(of(undefined));

        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'info', 'warn', 'error']);
        const confirmService = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible', 'confirmDestructive']);
        const dialogService = { open: jasmine.createSpy('open').and.returnValue({ onClose: of(dialogResult) }) };
        const apiErrors = jasmine.createSpyObj<ApiErrorService>('ApiErrorService', ['handledMessage']);

        const component = TestBed.runInInjectionContext(() => new FinancePayoutsComponent(payoutService, toastService, confirmService, dialogService as unknown as DialogService, apiErrors));
        return { component, payoutService, toastService, confirmService, dialogService, apiErrors };
    }

    describe('simulation', () => {
        it('asks the backend at every change, once the typing pauses for 300 ms', fakeAsync(() => {
            const { component, payoutService } = create();
            component.ngOnInit();

            component['selectEvent'](70);
            expect(payoutService.getEvent).toHaveBeenCalledOnceWith(70, { sharePercent: null, roundingStep: null }, jasmine.anything());

            component['onStepPreview'](10);
            component['onPercentPreview'](25);
            tick(299);
            expect(payoutService.getEvent).toHaveBeenCalledTimes(1);
            tick(1);
            expect(payoutService.getEvent).toHaveBeenCalledTimes(2);
            expect(payoutService.getEvent.calls.mostRecent().args[1]).toEqual({ sharePercent: 25, roundingStep: 10 });
        }));

        it('keeps the open event and the saved step among the choices, with stable lists', () => {
            const { component, payoutService } = create();
            payoutService.getEvent.and.returnValue(of(simulation({ savedRoundingStep: 3, roundingStep: 3 })));
            component.events = [{ eventId: 71, eventName: 'Serata benefica' } as FinancialEventSummary];
            component.ngOnInit();

            component['selectEvent'](70);

            expect(component['eventOptions']).toEqual([
                { eventId: 70, eventName: 'Concerto di primavera' },
                { eventId: 71, eventName: 'Serata benefica' }
            ]);
            expect(component['stepOptions'].map((option) => option.value)).toEqual([0, 1, 2, 3, 5, 10, 20]);
            expect(component['eventOptions']).toBe(component['eventOptions']);
        });

        it('shows the figures of the backend as they come, without computing amounts', fakeAsync(() => {
            const { component, payoutService } = create();
            payoutService.getEvent.and.returnValue(of(simulation({ dueTotal: 999 })));
            component.ngOnInit();

            component['selectEvent'](70);

            expect(component['simulation']?.dueTotal).toBe(999);
            expect(component['bars'].map((bar) => bar.segments.find((segment) => segment.key === 'shares')?.amount)).toEqual([315, 155]);
        }));

        it('adds every bar up to its own base, on one scale for both', () => {
            const { component } = create();

            const [planned, actual] = component['buildBars'](simulation());

            expect(planned.segments.reduce((total, segment) => total + segment.amount, 0)).toBe(1000);
            expect(actual.segments.reduce((total, segment) => total + segment.amount, 0)).toBe(800);
            expect(planned.segments.reduce((total, segment) => total + segment.width, 0)).toBeCloseTo(100, 6);
            expect(actual.segments.reduce((total, segment) => total + segment.width, 0)).toBeCloseTo(80, 6);
            expect(planned.segments.find((segment) => segment.key === 'association')?.percent).toBeCloseTo(20, 6);
        });

        it('draws the deficit beyond the line of the base', () => {
            const { component } = create();
            const deficit = distribution({ base: 100, expenses: 80, fixedPayments: 120, margin: -100, deficit: 100 });

            const [planned] = component['buildBars'](simulation({ planned: deficit, actual: deficit }));

            expect(planned.segments.reduce((total, segment) => total + segment.amount, 0)).toBe(planned.base + planned.deficit);
            expect(planned.baseLine).toBe(50);
            expect(planned.deficitWidth).toBe(50);
        });

        it('keeps the totals of the table equal to the segments of the bars', () => {
            const { component } = create();
            const event = simulation();

            const [planned, actual] = component['buildBars'](event);
            const shares = event.lines.filter((row) => row.kind === 'SHARE');
            const fixed = event.lines.filter((row) => row.kind === 'FIXED');

            expect(shares.reduce((total, row) => total + row.simulatedShare, 0)).toBe(planned.segments.find((segment) => segment.key === 'shares')!.amount);
            expect(shares.reduce((total, row) => total + row.realShare, 0)).toBe(actual.segments.find((segment) => segment.key === 'shares')!.amount);
            expect(fixed.reduce((total, row) => total + row.realShare, 0)).toBe(actual.segments.find((segment) => segment.key === 'fixed')!.amount);
        });

        it('shows the full association share next to a reduced one', () => {
            const { component } = create();
            const reduced = distribution({ base: 500, expenses: 300, fixedPayments: 150, margin: 50, fullAssociationShare: 100, associationShare: 50, associationShareReduced: true });

            const [planned] = component['buildBars'](simulation({ planned: reduced, actual: reduced }));
            const notices = component['buildNotices'](simulation({ planned: reduced, actual: reduced }));

            expect(planned.segments.find((segment) => segment.key === 'association')?.full).toBe(100);
            expect(notices.map((notice) => notice.key)).toContain('reducedShare');
        });

        it('explains the particular states as information, each once', () => {
            const { component } = create();
            const noFee = distribution({ fixedPayments: 120, expenses: 30, margin: -150, deficit: 150 });

            expect(component['buildNotices'](simulation())).toEqual([]);
            expect(component['buildNotices'](simulation({ incomeRecorded: false })).map((notice) => notice.key)).toEqual(['incomeMissing']);
            expect(component['buildNotices'](simulation({ planned: noFee, actual: noFee })).map((notice) => notice.key)).toEqual(['noFee']);
            expect(component['buildNotices'](simulation({ lines: [] })).map((notice) => notice.key)).toEqual(['noParticipants']);
            expect(component['buildNotices'](simulation({ deviationTotal: 20 })).map((notice) => notice.key)).toEqual(['deviation']);

            const deficit = distribution({ base: 100, expenses: 150, margin: -50, deficit: 50 });
            expect(component['buildNotices'](simulation({ actual: deficit })).map((notice) => notice.severity)).toEqual(['warn']);

            const belowStep = simulation({
                lines: [line({ realTheoreticalShare: 3, realShare: 0, simulatedTheoreticalShare: 3, simulatedShare: 0 })]
            });
            expect(component['buildNotices'](belowStep).map((notice) => notice.key)).toEqual(['belowStep']);
        });

        it('turns a missing configuration into an explanation with the way out', () => {
            const { component, payoutService, apiErrors } = create();
            payoutService.getEvent.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
            apiErrors.handledMessage.and.returnValue('Nessuna versione dei parametri dei compensi è applicabile');
            component.ngOnInit();

            component['selectEvent'](70);

            expect(component['notConfiguredMessage']).toContain('Nessuna versione');
            expect(component['simulation']).toBeUndefined();
            component['changeView']('settings');
            expect(payoutService.getSettings).toHaveBeenCalled();
        });

        it('previews a step without saving anything and goes back to the saved one', fakeAsync(() => {
            const { component, payoutService } = create();
            component.ngOnInit();
            component['selectEvent'](70);

            component['onStepPreview'](20);
            tick(300);
            component['resetPreview']();

            expect(payoutService.getEvent.calls.argsFor(1)[1]).toEqual({ sharePercent: null, roundingStep: 20 });
            expect(payoutService.getEvent.calls.mostRecent().args[1]).toEqual({ sharePercent: null, roundingStep: null });
            expect(payoutService.createSetting).not.toHaveBeenCalled();
            expect(payoutService.updateSetting).not.toHaveBeenCalled();
        }));

        it('saves a preview only as an explicit new version', () => {
            const { component, dialogService, payoutService } = create({ request: { validFrom: '2026-11-13', associationSharePercent: 15, roundingStep: 10 } });
            component.ngOnInit();
            component['selectEvent'](70);

            component['savePreviewAsVersion']();

            expect(dialogService.open.calls.mostRecent().args[0]).toBe(FinancePayoutSettingDialogComponent);
            expect(dialogService.open.calls.mostRecent().args[1].inputValues.proposal).toEqual({ associationSharePercent: 20, roundingStep: 5 });
            expect(payoutService.createSetting).toHaveBeenCalledWith({ validFrom: '2026-11-13', associationSharePercent: 15, roundingStep: 10 }, jasmine.anything());
        });
    });

    describe('factor and fixed payment', () => {
        function editing() {
            const created = create();
            created.component.ngOnInit();
            created.component['selectEvent'](70);
            const anna = simulation().lines[0];
            created.component['startEdit'](anna);
            return { ...created, anna };
        }

        it('refuses a factor above one', () => {
            const { component, payoutService, anna } = editing();
            component['editing']!.mode = 'FACTOR';
            component['editing']!.factor = 1.2;
            component['editing']!.reason = 'Prova';

            component['saveEdit'](anna);

            expect(component['factorError']).toBe('Il fattore è compreso fra 0 e 1.');
            expect(payoutService.setAdjustment).not.toHaveBeenCalled();
        });

        it('refuses a fixed payment that is not positive and one without a reason', () => {
            const { component, payoutService, anna } = editing();
            component['editing']!.mode = 'FIXED';
            component['editing']!.fixedAmount = 0;

            component['saveEdit'](anna);

            expect(component['fixedAmountError']).toBeDefined();
            expect(component['reasonError']).toBe('Il motivo è obbligatorio.');
            expect(payoutService.setAdjustment).not.toHaveBeenCalled();
        });

        it('refuses a fixed payment with more than fifteen integer digits, as the backend does', () => {
            const { component, payoutService, anna } = editing();
            component['editing']!.mode = 'FIXED';
            component['editing']!.reason = 'Ingaggio';
            component['editing']!.fixedAmount = 1e15;

            component['saveEdit'](anna);

            expect(component['fixedAmountError']).toBe('Indica un importo con al massimo 15 cifre prima della virgola.');
            expect(payoutService.setAdjustment).not.toHaveBeenCalled();

            component['editing']!.fixedAmount = 999_999_999_999_999;
            expect(component['fixedAmountError']).toBeUndefined();
        });

        it('sends either the factor or the fixed payment, never both, and simulates again', () => {
            const { component, payoutService, toastService, anna } = editing();
            component['editing']!.mode = 'FACTOR';
            component['editing']!.factor = 0.5;
            component['editing']!.mode = 'FIXED';
            component['editing']!.fixedAmount = 120;
            component['editing']!.reason = '  Ingaggio  ';

            component['saveEdit'](anna);

            expect(payoutService.setAdjustment).toHaveBeenCalledWith(70, 1, { factor: null, fixedAmount: 120, reason: 'Ingaggio' }, jasmine.anything());
            expect(toastService.success).toHaveBeenCalledWith('Pagamento fisso assegnato', jasmine.any(String));
            expect(component['editing']).toBeUndefined();
            expect(payoutService.getEvent).toHaveBeenCalledTimes(2);
        });

        it('removes an adjustment when the full share is chosen again', () => {
            const { component, payoutService } = editing();
            const bruno = simulation().lines[1];
            component['startEdit'](bruno);
            expect(component['editing']?.mode).toBe('FACTOR');
            component['editing']!.mode = 'NONE';

            component['saveEdit'](bruno);

            expect(payoutService.removeAdjustment).toHaveBeenCalledWith(70, 2, jasmine.anything());
            expect(payoutService.setAdjustment).not.toHaveBeenCalled();
        });

        it('calls nothing when a full share stays full', () => {
            const { component, payoutService, anna } = editing();

            component['saveEdit'](anna);

            expect(payoutService.removeAdjustment).not.toHaveBeenCalled();
            expect(payoutService.setAdjustment).not.toHaveBeenCalled();
            expect(component['editing']).toBeUndefined();
        });
    });

    describe('shares of a member', () => {
        const settlement: PayoutSettlement = {
            movementId: 99,
            userId: 1,
            userName: 'Anna Bianchi',
            bookingDate: '2026-11-13',
            total: 125,
            eventCount: 2,
            lines: [
                { id: 1, eventId: 70, eventName: 'Concerto di primavera', userId: 1, amount: 75 },
                { id: 2, eventId: 71, eventName: 'Serata benefica', userId: 1, amount: 50 }
            ]
        };

        it('opens the member named by the link and proposes the residual of each line', () => {
            const { component, payoutService } = create();
            component.initialUserId = 1;

            component.ngOnInit();

            expect(component['view']).toBe('members');
            expect(payoutService.getMembers).toHaveBeenCalled();
            expect(payoutService.getMember).toHaveBeenCalledWith(1, { from: undefined, to: undefined, status: 'ALL' });
            expect(component['rows'].map((row) => row.amount)).toEqual([105, 50, null]);
        });

        it('selects only what is still due and refuses more than the residual', () => {
            const { component } = create();
            component.ngOnInit();
            component['openMember'](1);

            component['selectAllDue']();
            expect(component['selectedRows'].map((row) => row.line.eventId)).toEqual([70, 71]);
            expect(component['isSelectable']({ data: component['rows'][2] })).toBeFalse();
            expect(component['selectedTotal']).toBe(155);

            component['rows'][1].amount = 60;
            expect(component['amountError'](component['rows'][1])).toContain('Non oltre il dovuto');
            expect(component['selectionInvalid']).toBeTrue();
        });

        it('generates one movement for the chosen lines and announces it', () => {
            const dialogResult = { accountId: 4, bookingDate: '2026-11-13', categoryId: null, description: null, notes: null };
            const { component, payoutService, dialogService, toastService } = create(dialogResult);
            payoutService.settle.and.returnValue(of(settlement));
            const settled = jasmine.createSpy('settled');
            component.settled.subscribe(settled);
            component.ngOnInit();
            component['openMember'](1);
            component['selectAllDue']();
            component['rows'][0].amount = 75;

            component['openSettlement']();

            const config = dialogService.open.calls.mostRecent().args[1];
            expect(dialogService.open.calls.mostRecent().args[0]).toBe(FinancePayoutSettlementDialogComponent);
            expect(config.inputValues.total).toBe(125);
            expect(config.inputValues.lines.length).toBe(2);
            const request = payoutService.settle.calls.mostRecent().args[0];
            expect(request.accountId).toBe(4);
            expect(request.requestKey).toMatch(/^[0-9a-f-]{36}$/);
            expect(request.lines).toEqual([
                { eventId: 70, userId: 1, amount: 75 },
                { eventId: 71, userId: 1, amount: 50 }
            ]);
            expect(settled).toHaveBeenCalledWith(settlement);
            expect(toastService.success).toHaveBeenCalledWith('Movimento generato', jasmine.stringContaining('2 eventi'));
            expect(payoutService.getMember).toHaveBeenCalledTimes(2);
        });

        it('registers nothing when the dialog is cancelled', () => {
            const { component, payoutService } = create(undefined);
            component.ngOnInit();
            component['openMember'](1);
            component['selectAllDue']();

            component['openSettlement']();

            expect(payoutService.settle).not.toHaveBeenCalled();
        });

        it('labels each line by what is left to do', () => {
            const { component } = create();
            const [due, partial, settled] = member.lines;

            expect(component['lineStatus'](due).label).toBe('Da liquidare');
            expect(component['lineStatus'](partial).label).toBe('Parziale');
            expect(component['lineStatus'](settled).label).toBe('Liquidata');
            expect(component['lineStatus']({ ...due, due: 0, realShare: 0, incomeRecorded: false }).label).toBe('In attesa d’incasso');
            expect(component['lineStatus']({ ...due, due: 0, configured: false }).label).toBe('Parametri mancanti');
            expect(component['lineStatus']({ ...due, deviation: 10 }).label).toBe('Scostamento');
        });
    });

    describe('parameters', () => {
        it('opens on the versions when the link asks for them', () => {
            const { component, payoutService } = create();
            component.initialView = 'settings';

            component.ngOnInit();

            expect(component['view']).toBe('settings');
            expect(payoutService.getSettings).toHaveBeenCalled();
            expect(payoutService.getEvent).not.toHaveBeenCalled();
        });

        it('creates a new version and edits an existing one', () => {
            const request = { validFrom: '2026-01-01', associationSharePercent: 20, roundingStep: 5 };
            const created = create({ request });
            created.component.ngOnInit();
            created.component['openSetting']();
            expect(created.payoutService.createSetting).toHaveBeenCalledWith(request, jasmine.anything());

            const edited = create({ id: 4, request });
            edited.component.ngOnInit();
            edited.component['openSetting']({ id: 4, ...request });
            expect(edited.payoutService.updateSetting).toHaveBeenCalledWith(4, request, jasmine.anything());
            expect(edited.toastService.success).toHaveBeenCalledWith('Parametri salvati', 'Valgono dal 01/01/2026.');
        });

        it('deletes a version only after the confirmation', () => {
            const { component, payoutService, confirmService } = create();
            component.ngOnInit();

            component['deleteSetting']({ id: 4, validFrom: '2026-01-01', associationSharePercent: 20, roundingStep: 5 });
            expect(payoutService.deleteSetting).not.toHaveBeenCalled();
            confirmService.confirmDestructive.calls.mostRecent().args[0].accept();

            expect(payoutService.deleteSetting).toHaveBeenCalledWith(4, jasmine.anything());
            expect(payoutService.getSettings).toHaveBeenCalled();
        });

        it('lists the versions from the most recent', () => {
            const { component, payoutService } = create();
            payoutService.getSettings.and.returnValue(
                of([
                    { id: 1, validFrom: '2025-01-01', associationSharePercent: 20, roundingStep: 5 },
                    { id: 2, validFrom: '2026-01-01', associationSharePercent: 15, roundingStep: 10 }
                ])
            );

            component['loadSettings']();

            expect(component['settings'].map((setting) => setting.id)).toEqual([2, 1]);
        });
    });
});
