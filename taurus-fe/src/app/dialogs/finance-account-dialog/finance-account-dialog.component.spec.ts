import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FinancialAccount } from '../../module';
import { AccountDialogResult, FinanceAccountDialogComponent } from './finance-account-dialog.component';

describe('FinanceAccountDialogComponent', () => {
    function create(account?: FinancialAccount): { component: FinanceAccountDialogComponent; close: jasmine.Spy } {
        const close = jasmine.createSpy('close');
        const component = new FinanceAccountDialogComponent(
            { close } as unknown as DynamicDialogRef<FinanceAccountDialogComponent>,
            { inputValues: { account } } as unknown as DynamicDialogConfig<never, never>
        );
        return { component, close };
    }

    it('requires a name and a three-letter currency', () => {
        const { component, close } = create();

        expect(component['invalidCount']).toBe(1);
        component['account'].name = 'Cassa';
        component['account'].currency = 'EURO';
        expect(component['invalidCount']).toBe(1);

        component['save']();
        expect(close).not.toHaveBeenCalled();
    });

    it('returns the opening document together with the account it justifies', () => {
        const { component, close } = create();
        component['account'].name = ' Cassa ';
        component['account'].currency = 'eur';
        component['account'].initialBalance = 250;
        component['account'].initialBalanceDescription = 'Fondo cassa al passaggio di gestione';
        component['openingDate'] = new Date(2026, 0, 1);
        const file = new File([new Uint8Array([1])], 'verbale.pdf', { type: 'application/pdf' });
        component['onOpeningAttachmentSelected']({ target: { files: [file] } } as unknown as Event);

        component['save']();

        const result = close.calls.mostRecent().args[0] as AccountDialogResult;
        expect(result.account.name).toBe('Cassa');
        expect(result.account.currency).toBe('EUR');
        expect(result.account.initialBalanceDate).toBe('2026-01-01');
        expect(result.openingAttachment).toBe(file);
        expect(result.openingAttachmentDescription).toBe('Fondo cassa al passaggio di gestione');
    });

    it('does not attach anything when an existing account is edited', () => {
        const { component, close } = create({ id: 7, name: 'Cassa', accountType: 'CASH', currency: 'EUR' });
        const file = new File([new Uint8Array([1])], 'verbale.pdf', { type: 'application/pdf' });
        component['onOpeningAttachmentSelected']({ target: { files: [file] } } as unknown as Event);

        component['save']();

        const result = close.calls.mostRecent().args[0] as AccountDialogResult;
        expect(component['editing']).toBeTrue();
        expect(result.openingAttachment).toBeUndefined();
        expect(result.account.initialBalanceDate).toBeUndefined();
    });

    it('closes empty when the user gives up', () => {
        const { component, close } = create();

        component['cancel']();

        expect(close).toHaveBeenCalledWith();
    });
});
