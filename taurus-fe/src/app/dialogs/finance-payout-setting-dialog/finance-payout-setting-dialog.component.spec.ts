import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { PayoutSettledEvent } from '../../module';
import { PayoutService } from '../../service';
import { FinancePayoutSettingDialogComponent, PayoutSettingDialogData, PayoutSettingDialogResult } from './finance-payout-setting-dialog.component';

describe('FinancePayoutSettingDialogComponent', () => {
    const settled: PayoutSettledEvent[] = [{ eventId: 70, eventName: 'Concerto', eventDate: '2026-04-18', settledAmount: 300 }];

    function create(data: PayoutSettingDialogData = {}, affected: PayoutSettledEvent[] = []) {
        const close = jasmine.createSpy('close');
        const payoutService = jasmine.createSpyObj<PayoutService>('PayoutService', ['getAffectedEvents']);
        payoutService.getAffectedEvents.and.returnValue(of(affected));
        const component = TestBed.runInInjectionContext(
            () => new FinancePayoutSettingDialogComponent({ close } as unknown as DynamicDialogRef<FinancePayoutSettingDialogComponent>, { inputValues: data } as unknown as DynamicDialogConfig<never, PayoutSettingDialogData>, payoutService)
        );
        return { component, close, payoutService };
    }

    it('starts a new version from today with the proposed values', () => {
        const { component } = create({ proposal: { associationSharePercent: 15, roundingStep: 10 } });

        expect(component['heading']).toBe('Nuova versione dei parametri');
        expect(component['associationSharePercent']).toBe(15);
        expect(component['roundingStep']).toBe(10);
        expect(component['retroactive']).toBeFalse();
    });

    it('refuses a percentage outside 0–100 and a negative step', () => {
        const { component, close } = create();
        expect(component['percentError']).toBeDefined();

        component['associationSharePercent'] = 120;
        component['roundingStep'] = -1;
        component['save']();

        expect(component['percentError']).toBe('La percentuale è compresa fra 0 e 100.');
        expect(component['stepError']).toBeDefined();
        expect(close).not.toHaveBeenCalled();
    });

    it('warns which settled events a retroactive date recalculates, excluding the version being edited', fakeAsync(() => {
        const { component, payoutService } = create({ setting: { id: 4, validFrom: '2026-01-01', associationSharePercent: 20, roundingStep: 5 } }, settled);

        component.ngOnInit();
        tick(300);

        expect(payoutService.getAffectedEvents).toHaveBeenCalledWith('2026-01-01', 4);
        expect(component['affectedEvents']).toEqual(settled);
        expect(component['retroactive']).toBeTrue();

        component['validFrom'] = new Date(2025, 5, 1);
        component['onValidFromChange']();
        component['onValidFromChange']();
        tick(300);
        expect(payoutService.getAffectedEvents).toHaveBeenCalledTimes(2);
        expect(payoutService.getAffectedEvents.calls.mostRecent().args[0]).toBe('2025-06-01');
    }));

    it('returns the version with its id when edited', () => {
        const { component, close } = create({ setting: { id: 4, validFrom: '2026-01-01', associationSharePercent: 20, roundingStep: 5 } });
        component['associationSharePercent'] = 25;

        component['save']();

        const result = close.calls.mostRecent().args[0] as PayoutSettingDialogResult;
        expect(result).toEqual({ id: 4, request: { validFrom: '2026-01-01', associationSharePercent: 25, roundingStep: 5 } });
    });
});
