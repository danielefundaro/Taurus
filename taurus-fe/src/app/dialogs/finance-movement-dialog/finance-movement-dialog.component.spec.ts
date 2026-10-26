import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { FinancialAccount, FinancialAttachment, FinancialCategory, FinancialDirection, FinancialMovement } from '../../module';
import { ConfirmService, FinanceService, ToastService } from '../../service';
import { FinanceMovementDialogComponent, MovementDialogResult } from './finance-movement-dialog.component';

const accounts: FinancialAccount[] = [
    { id: 10, name: 'Cassa', accountType: 'CASH', currency: 'EUR', active: true },
    { id: 11, name: 'Conto chiuso', accountType: 'BANK', currency: 'EUR', active: false }
];

describe('FinanceMovementDialogComponent', () => {
    function create(
        values: {
            movement?: FinancialMovement;
            categories?: FinancialCategory[];
            eventId?: number;
            direction?: FinancialDirection;
            categoryId?: number;
        } = {}
    ): { component: FinanceMovementDialogComponent; close: jasmine.Spy; financeService: jasmine.SpyObj<FinanceService> } {
        const close = jasmine.createSpy('close');
        const financeService = jasmine.createSpyObj<FinanceService>('FinanceService', ['getAttachments', 'uploadAttachment']);
        financeService.getAttachments.and.returnValue(of([] as FinancialAttachment[]));
        const component = new FinanceMovementDialogComponent(
            { close } as unknown as DynamicDialogRef<FinanceMovementDialogComponent>,
            {
                inputValues: {
                    movement: values.movement,
                    accounts,
                    categories: values.categories ?? [],
                    events: [],
                    eventId: values.eventId,
                    direction: values.direction,
                    categoryId: values.categoryId
                }
            } as unknown as DynamicDialogConfig<never, never>,
            financeService,
            jasmine.createSpyObj<ToastService>('ToastService', ['success']),
            jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmDestructive'])
        );
        return { component, close, financeService };
    }

    it('proposes the direction of the action that opened the form', () => {
        const { component } = create({ eventId: 70, direction: 'INCOME' });

        expect(component['movement'].direction).toBe('INCOME');
        expect(component['movement'].eventId).toBe(70);
    });

    it('preselects the only category coherent with the proposed direction', () => {
        const categories: FinancialCategory[] = [
            { id: 20, name: 'Quote', direction: 'INCOME' },
            { id: 21, name: 'Rimborsi', direction: 'EXPENSE' },
            { id: 22, name: 'Cachet', direction: 'EXPENSE', active: false }
        ];

        expect(create({ direction: 'INCOME', categories })['component']['movement'].categoryId).toBe(20);
        // Due categorie utilizzabili: la scelta resta all'utente.
        expect(create({ direction: 'EXPENSE', categories: [...categories, { id: 23, name: 'Varie', direction: 'BOTH' }] })['component']['movement'].categoryId)
            .toBeUndefined();
    });

    it('keeps the category chosen by the caller and leaves an existing movement untouched', () => {
        const categories: FinancialCategory[] = [{ id: 20, name: 'Quote', direction: 'INCOME' }];

        expect(create({ direction: 'INCOME', categories, categoryId: 99 })['component']['movement'].categoryId).toBe(99);
        expect(
            create({ movement: { id: 7, accountId: 10, direction: 'EXPENSE', bookingDate: '2026-09-01', amount: 10, description: 'Service' }, direction: 'INCOME', categories })[
                'component'
            ]['movement'].direction
        ).toBe('EXPENSE');
    });

    it('offers only the active accounts and the categories of the chosen direction', () => {
        const { component } = create({
            direction: 'EXPENSE',
            categories: [
                { id: 20, name: 'Quote', direction: 'INCOME' },
                { id: 21, name: 'Rimborsi', direction: 'EXPENSE' },
                { id: 23, name: 'Varie', direction: 'BOTH' }
            ]
        });

        expect(component['activeAccounts'].map((account) => account.id)).toEqual([10]);
        expect(component['movementCategories'].map((category) => category.id)).toEqual([21, 23]);
    });

    it('refuses to close while the required fields are missing', () => {
        const { component, close } = create();

        expect(component['invalidCount']).toBe(3);
        component['save']();
        expect(close).not.toHaveBeenCalled();

        component['movement'].accountId = 10;
        component['movement'].amount = 25;
        component['movement'].description = '  Service audio  ';
        component['save']();

        const result = close.calls.mostRecent().args[0] as MovementDialogResult;
        expect(result.movement.description).toBe('Service audio');
        expect(result.movement.bookingDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });
});
