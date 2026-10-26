import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import {
    FinancialAccount,
    FinancialAttachment,
    FinancialCategory,
    FinancialDashboard,
    FinancialEventStatement,
    FinancialEventSummary,
    FinancialMovement,
    Page
} from '../../module';
import { ConfirmService, FinanceService, ToastService } from '../../service';
import { FinanceComponent } from './finance.component';

type Finance = jasmine.SpyObj<FinanceService>;

const account = (id: number, name: string, balance: number): FinancialAccount => ({
    id,
    name,
    accountType: 'CASH',
    currency: 'EUR',
    active: true,
    balance
});

const movement = (overrides: Partial<FinancialMovement> = {}): FinancialMovement => ({
    accountId: 10,
    direction: 'EXPENSE',
    bookingDate: '2026-09-01',
    amount: 50,
    description: 'Service audio',
    ...overrides
});

describe('FinanceComponent', () => {
    beforeEach(() =>
        TestBed.configureTestingModule({
            providers: [{ provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: new Map() } } }]
        })
    );

    function create(): {
        component: FinanceComponent;
        financeService: Finance;
        toastService: jasmine.SpyObj<ToastService>;
        confirmService: jasmine.SpyObj<ConfirmService>;
    } {
        const financeService = jasmine.createSpyObj<FinanceService>('FinanceService', [
            'createMovement',
            'updateMovement',
            'getEventStatement',
            'exportEventStatement',
            'exportCashbook',
            'rollover',
            'getDashboard',
            'getAccounts',
            'getCategories',
            'getEvents',
            'getYears',
            'getMovements',
            'reorderAccounts',
            'reorderCategories',
            'uploadAttachment'
        ]);
        financeService.createMovement.and.callFake((saved: FinancialMovement) => of({ ...saved, id: 99 }));
        financeService.updateMovement.and.callFake((saved: FinancialMovement) => of(saved));
        financeService.reorderAccounts.and.returnValue(of([]));
        financeService.reorderCategories.and.returnValue(of([]));
        financeService.getDashboard.and.returnValue(of({} as unknown as FinancialDashboard));
        financeService.getAccounts.and.returnValue(of([]));
        financeService.getCategories.and.returnValue(of([]));
        financeService.getEvents.and.returnValue(of({ content: [], totalElements: 0 } as unknown as Page<FinancialEventSummary>));
        financeService.getYears.and.returnValue(of([]));
        financeService.getMovements.and.returnValue(of({ content: [], totalElements: 0 } as unknown as Page<FinancialMovement>));
        financeService.getEventStatement.and.returnValue(
            of({ summary: { eventId: 70, eventName: 'Concerto' }, feeVariance: 0, costVariance: 0, resultVariance: 0 } as unknown as FinancialEventStatement)
        );
        financeService.exportEventStatement.and.returnValue(of(new Blob()));
        financeService.exportCashbook.and.returnValue(of(new Blob()));
        financeService.rollover.and.returnValue(of({ year: 2025, startDate: '2025-01-01', endDate: '2025-12-31', status: 'ROLLED_OVER' }));
        financeService.uploadAttachment.and.returnValue(of({ id: 1 } as unknown as FinancialAttachment));

        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'info', 'warn', 'error']);
        const confirmService = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible', 'confirmDestructive']);

        const component = TestBed.runInInjectionContext(
            () =>
                new FinanceComponent(
                    financeService,
                    toastService,
                    confirmService,
                    {} as DialogService,
                    TestBed.inject(ActivatedRoute)
                )
        );
        return { component, financeService, toastService, confirmService };
    }

    it('generates an idempotency key only for a new movement', () => {
        const { component, financeService } = create();

        component['saveMovement']({ movement: movement() });
        component['saveMovement']({ movement: movement({ id: 7, version: 3 }) });

        expect(financeService.createMovement.calls.mostRecent().args[0].requestKey).toBeTruthy();
        expect(financeService.updateMovement.calls.mostRecent().args[0].requestKey).toBeUndefined();
        expect(financeService.updateMovement.calls.mostRecent().args[0].version).toBe(3);
    });

    it('announces the recalculated openings only when the movement belongs to a past year', () => {
        const { component, toastService } = create();

        component['noticeRecalculatedOpenings'](movement({ accountingYear: 2026 }));
        expect(toastService.info).not.toHaveBeenCalled();

        component['noticeRecalculatedOpenings'](movement({ accountingYear: 2024 }));
        expect(toastService.info).toHaveBeenCalled();
    });

    it('warns only when an outflow takes the account below zero', () => {
        const { component } = create();
        component['accounts'] = [account(10, 'Cassa', 30)];

        expect(component['negativeBalanceWarning'](movement({ amount: 20 }))).toBeUndefined();
        expect(component['negativeBalanceWarning'](movement({ amount: 50 }))).toContain('Cassa');
        // Un'entrata non può portare il conto sotto zero, e una modifica non è un nuovo esborso.
        expect(component['negativeBalanceWarning'](movement({ amount: 50, direction: 'INCOME' }))).toBeUndefined();
        expect(component['negativeBalanceWarning'](movement({ amount: 50, id: 7 }))).toBeUndefined();
    });

    it('moves an element one position and leaves the ends alone', () => {
        const { component } = create();

        expect(component['reordered']([1, 2, 3], 3, -1)).toEqual([1, 3, 2]);
        expect(component['reordered']([1, 2, 3], 1, 1)).toEqual([2, 1, 3]);
        expect(component['reordered']([1, 2, 3], 1, -1)).toBeUndefined();
        expect(component['reordered']([1, 2, 3], 3, 1)).toBeUndefined();
    });

    it('sends the whole order when a category moves', () => {
        const { component, financeService } = create();
        const categories: FinancialCategory[] = [
            { id: 20, name: 'Quote', direction: 'INCOME' },
            { id: 21, name: 'Rimborsi', direction: 'EXPENSE' }
        ];
        component['categories'] = categories;

        component['moveCategory'](categories[1], -1);

        expect(financeService.reorderCategories).toHaveBeenCalledWith([21, 20]);
    });

    it('loads the event statement and keeps it aligned after a movement changes', () => {
        const { component, financeService } = create();

        component['showEventStatement']({ eventId: 70 } as FinancialEventSummary);
        expect(component['eventStatement']?.summary.eventName).toBe('Concerto');

        component['refreshAfterMovement']();
        expect(financeService.getEventStatement).toHaveBeenCalledTimes(2);

        component['exportEventStatement']('pdf');
        expect(financeService.exportEventStatement).toHaveBeenCalledWith('pdf', 70);

        component['closeEventStatement']();
        expect(component['eventStatement']).toBeUndefined();
    });

    it('asks for confirmation before regenerating a rollover and reports it only once accepted', () => {
        const { component, financeService, confirmService, toastService } = create();

        component['rolloverYear'](2025);
        expect(financeService.rollover).not.toHaveBeenCalled();

        confirmService.confirmReversible.calls.mostRecent().args[0].accept();
        expect(financeService.rollover).toHaveBeenCalledWith(2025);
        expect(toastService.success).toHaveBeenCalled();
    });

    it('sends the user to the movements of a single event', () => {
        const { component, financeService } = create();

        component['filterMovementsByEvent'](70);

        expect(component['activeTab']).toBe('movements');
        expect(financeService.getMovements.calls.mostRecent().args[0].eventId).toBe(70);
    });

    it('reloads the dashboard on the chosen period', () => {
        const { component, financeService } = create();
        component['dashboardFrom'] = new Date(2025, 0, 1);
        component['dashboardTo'] = new Date(2025, 11, 31);

        component['reloadDashboard']();

        expect(financeService.getDashboard).toHaveBeenCalledWith('2025-01-01', '2025-12-31');
    });

    it('exports the cashbook with the filters currently applied', () => {
        const { component, financeService } = create();
        component['movementFilters'] = { year: 2026 };

        component['exportCashbook']('csv');

        expect(financeService.exportCashbook).toHaveBeenCalledWith('csv', jasmine.objectContaining({ year: 2026 }));
    });

    it('carries every active filter into the export', () => {
        const { component } = create();
        component['movementFilters'] = {
            from: new Date(2026, 0, 1),
            year: 2026,
            nature: 'ORDINARY',
            counterparty: 'Rossi',
            reconciled: false
        };

        expect(component['currentMovementFilters']()).toEqual(
            jasmine.objectContaining({ from: '2026-01-01', year: 2026, nature: 'ORDINARY', counterparty: 'Rossi', reconciled: false })
        );
    });
});
