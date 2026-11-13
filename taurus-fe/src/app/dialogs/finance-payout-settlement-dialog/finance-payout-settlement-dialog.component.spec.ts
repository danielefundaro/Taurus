import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FinancialAccount, FinancialCategory } from '../../module';
import { FinancePayoutSettlementDialogComponent, PayoutSettlementDialogData, PayoutSettlementDialogResult } from './finance-payout-settlement-dialog.component';

const account = (id: number, active = true): FinancialAccount => ({ id, name: `Conto ${id}`, accountType: 'BANK', currency: 'EUR', active });
const category = (id: number, direction: FinancialCategory['direction'], active = true): FinancialCategory => ({ id, name: `Categoria ${id}`, direction, active });

describe('FinancePayoutSettlementDialogComponent', () => {
    function create(data: Partial<PayoutSettlementDialogData> = {}): { component: FinancePayoutSettlementDialogComponent; close: jasmine.Spy } {
        const close = jasmine.createSpy('close');
        const inputValues: PayoutSettlementDialogData = {
            memberName: 'Anna Bianchi',
            lines: [
                { eventName: 'Concerto', eventDate: '2026-04-18', amount: 75 },
                { eventName: 'Serata', eventDate: '2026-05-02', amount: 50 }
            ],
            total: 125,
            accounts: [account(1), account(2)],
            categories: [],
            ...data
        };
        const component = new FinancePayoutSettlementDialogComponent({ close } as unknown as DynamicDialogRef<FinancePayoutSettlementDialogComponent>, { inputValues } as unknown as DynamicDialogConfig<never, PayoutSettlementDialogData>);
        return { component, close };
    }

    it('declares the total and how many events it covers', () => {
        const { component } = create();

        expect(component['total']).toBe(125);
        expect(component['eventsLabel']).toBe('2 eventi');
        expect(create({ lines: [{ eventName: 'Concerto', eventDate: '2026-04-18', amount: 75 }] }).component['eventsLabel']).toBe('1 evento');
    });

    it('requires the account and offers only active accounts and expense categories', () => {
        const { component, close } = create({
            accounts: [account(1), account(2, false)],
            categories: [category(3, 'EXPENSE'), category(4, 'INCOME'), category(5, 'BOTH'), category(6, 'EXPENSE', false)]
        });

        expect(component['accounts'].map((item) => item.id)).toEqual([1]);
        expect(component['categories'].map((item) => item.id)).toEqual([3, 5]);
        // A single active account is proposed at once.
        expect(component['accountId']).toBe(1);

        const ambiguous = create();
        expect(ambiguous.component['invalidCount']).toBe(1);
        ambiguous.component['save']();
        expect(ambiguous.close).not.toHaveBeenCalled();
        expect(close).not.toHaveBeenCalled();
    });

    it('returns the movement data, leaving description and category to the defaults when empty', () => {
        const { component, close } = create();
        component['accountId'] = 2;
        component['bookingDate'] = new Date(2026, 10, 13);
        component['description'] = '   ';
        component['notes'] = ' Bonifico ';

        component['save']();

        const result = close.calls.mostRecent().args[0] as PayoutSettlementDialogResult;
        expect(result).toEqual({ accountId: 2, categoryId: null, bookingDate: '2026-11-13', description: null, notes: 'Bonifico' });
    });

    it('closes without a result when cancelled', () => {
        const { component, close } = create();

        component['cancel']();

        expect(close).toHaveBeenCalledWith();
    });
});
