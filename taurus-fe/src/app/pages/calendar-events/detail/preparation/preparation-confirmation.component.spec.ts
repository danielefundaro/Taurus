import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { RoleEnums } from '../../../../constants';
import { EventPreparationConfirmations, EventPreparationView } from '../../../../module';
import { EventPreparationService, KeycloakService } from '../../../../service';
import { PreparationConfirmationComponent } from './preparation-confirmation.component';

const confirmations = (overrides: Partial<EventPreparationConfirmations> = {}): EventPreparationConfirmations => ({
    budgetCurrent: false,
    presenceCurrent: false,
    noMovementsCurrent: false,
    ...overrides
});

describe('PreparationConfirmationComponent', () => {
    let fixture: ComponentFixture<PreparationConfirmationComponent>;
    let service: jasmine.SpyObj<EventPreparationService>;
    const view = { program: [] } as unknown as EventPreparationView;

    beforeEach(async () => {
        service = jasmine.createSpyObj<EventPreparationService>('EventPreparationService', ['confirmBudget', 'confirmPresence', 'confirmNoMovements']);
        service.confirmBudget.and.returnValue(of(view));
        service.confirmPresence.and.returnValue(of(view));
        service.confirmNoMovements.and.returnValue(of(view));
        await TestBed.configureTestingModule({
            imports: [PreparationConfirmationComponent],
            providers: [provideNoopAnimations(), { provide: EventPreparationService, useValue: service }, { provide: KeycloakService, useValue: { currentUserRole: RoleEnums.ADMIN } }]
        }).compileComponents();
        fixture = TestBed.createComponent(PreparationConfirmationComponent);
        fixture.componentRef.setInput('eventId', 42);
    });

    function render(area: 'budget' | 'presence' | 'noMovements', value?: EventPreparationConfirmations, role = RoleEnums.ADMIN): HTMLElement {
        fixture.componentRef.setInput('area', area);
        fixture.componentRef.setInput('confirmations', value);
        fixture.componentRef.setInput('role', role);
        fixture.detectChanges();
        return fixture.nativeElement;
    }

    const button = () => fixture.nativeElement.querySelector('p-button button') as HTMLButtonElement;

    it('asks for a first confirmation and sends the treasurer through the finance API', () => {
        const emitted: EventPreparationView[] = [];
        fixture.componentInstance.confirmed.subscribe((value) => emitted.push(value));
        const element = render('budget', confirmations(), RoleEnums.TREASURER);
        expect(element.textContent).toContain('Da confermare');

        button().click();

        expect(service.confirmBudget).toHaveBeenCalledWith(42, RoleEnums.TREASURER);
        expect(emitted).toEqual([view]);
    });

    it('marks a current confirmation and does not repeat it', () => {
        const element = render('budget', confirmations({ budgetAt: '2026-10-20T10:00:00+02:00', budgetCurrent: true }));

        expect(element.textContent).toContain('Confermato il 20/10/2026');
        expect(button().disabled).toBeTrue();
    });

    it('tells an obsolete confirmation apart and lets it be renewed', () => {
        const element = render('presence', confirmations({ presenceAt: '2026-10-20T10:00:00+02:00', presenceCurrent: false }));

        expect(element.textContent).toContain('Dati cambiati dopo la conferma');
        expect(button().textContent).toContain('Conferma presenze di nuovo');
        button().click();
        expect(service.confirmPresence).toHaveBeenCalledWith(42);
    });

    it('waits while the register has unsaved changes', () => {
        fixture.componentRef.setInput('blockedReason', 'Salva le presenze prima di confermare il registro.');
        const element = render('presence', confirmations());

        expect(element.textContent).toContain('Salva le presenze prima');
        expect(button().disabled).toBeTrue();
    });

    it('sends the optional reason of no movements, within its limit', () => {
        render('noMovements', confirmations({ noMovementsAt: '2026-10-20T10:00:00+02:00', noMovementsCurrent: true, noMovementsReason: 'Concerto gratuito' }), RoleEnums.TREASURER);
        const state = fixture.componentInstance as unknown as Record<string, any>;
        expect(fixture.nativeElement.textContent).toContain('Concerto gratuito');

        state['reason'] = 'x'.repeat(501);
        fixture.detectChanges();
        expect(button().disabled).toBeTrue();
        expect(fixture.nativeElement.textContent).toContain('al massimo 500 caratteri');

        state['reason'] = 'Nessuna spesa';
        fixture.detectChanges();
        button().click();
        expect(service.confirmNoMovements).toHaveBeenCalledWith(42, RoleEnums.TREASURER, 'Nessuna spesa');
        expect(state['reason']).toBe('');
    });
});
