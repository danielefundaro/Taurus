import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { RoleEnums } from '../../../../constants';
import { EventPreparationConfiguration, EventPreparationView } from '../../../../module';
import { ConfirmService, EventPreparationService, HANDLED_ERRORS, KeycloakService } from '../../../../service';
import { PreparationConfigurationComponent } from './preparation-configuration.component';
import { profileConfiguration } from './preparation-labels';

const saved = (overrides: Partial<EventPreparationConfiguration> = {}): EventPreparationConfiguration => ({
    ...profileConfiguration('PERFORMANCE'),
    materialsRequired: true,
    version: 3,
    ...overrides
});

describe('PreparationConfigurationComponent', () => {
    let fixture: ComponentFixture<PreparationConfigurationComponent>;
    let component: PreparationConfigurationComponent;
    let service: jasmine.SpyObj<EventPreparationService>;
    let confirm: jasmine.SpyObj<ConfirmService>;
    let dirty: boolean[];

    beforeEach(async () => {
        service = jasmine.createSpyObj<EventPreparationService>('EventPreparationService', ['configure']);
        confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible']);
        await TestBed.configureTestingModule({
            imports: [PreparationConfigurationComponent],
            providers: [provideNoopAnimations(), { provide: EventPreparationService, useValue: service }, { provide: ConfirmService, useValue: confirm }, { provide: KeycloakService, useValue: { currentUserRole: RoleEnums.ADMIN } }]
        }).compileComponents();
        fixture = TestBed.createComponent(PreparationConfigurationComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('eventId', 42);
        dirty = [];
        component.dirtyChange.subscribe((value) => dirty.push(value));
    });

    const state = () => component as unknown as Record<string, any>;

    it('asks for the profile first, then for every explicit choice before saving', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('Preparazione da configurare');

        state()['changeProfile']('REHEARSAL');
        fixture.detectChanges();

        expect(state()['undecided'].map((value: { key: string }) => value.key)).toEqual(['locationRequired', 'materialsRequired']);
        expect(state()['canSave']).toBeFalse();
        expect(fixture.nativeElement.textContent).toContain('Da scegliere: 2 aree.');
        expect(fixture.nativeElement.querySelectorAll('fieldset.border-orange-400').length).toBe(2);

        state()['setRequirement']('locationRequired', true);
        state()['setRequirement']('materialsRequired', false);
        expect(state()['canSave']).toBeTrue();
        expect(dirty).toEqual([true]);
    });

    it('keeps the requirements of a saved plan when the profile changes', () => {
        fixture.componentRef.setInput('configuration', saved());
        fixture.detectChanges();

        state()['changeProfile']('OTHER');

        expect(state()['draft'].profile).toBe('OTHER');
        expect(state()['draft'].programRequired).toBeTrue();
        expect(state()['draft'].materialsRequired).toBeTrue();
        expect(state()['undecided']).toEqual([]);
    });

    it('drops the scores with the program and asks for the minimum participants with the availability', () => {
        fixture.componentRef.setInput('configuration', saved({ availabilityRequired: false, minimumAvailableParticipants: undefined }));
        fixture.detectChanges();

        state()['setRequirement']('programRequired', false);
        expect(state()['draft'].scoresRequired).toBeFalse();
        expect(state()['disabledReason']('scoresRequired')).toBe('Gli spartiti richiedono il programma.');

        state()['setRequirement']('availabilityRequired', true);
        expect(state()['draft'].minimumAvailableParticipants).toBe(1);
        state()['draft'].minimumAvailableParticipants = undefined;
        expect(state()['participantsMissing']).toBeTrue();
        expect(state()['canSave']).toBeFalse();
    });

    it('locks the requirements of a module that is off', () => {
        fixture.componentRef.setInput('inventoryEnabled', false);
        fixture.componentRef.setInput('financeEnabled', false);
        fixture.detectChanges();

        state()['changeProfile']('PERFORMANCE');

        expect(state()['draft'].materialsRequired).toBeFalse();
        expect(state()['undecided']).toEqual([]);
        expect(state()['disabledReason']('materialsRequired')).toBe('Il modulo Inventario è disattivato.');
        expect(state()['disabledReason']('financialClosureRequired')).toBe('Il modulo Economia è disattivato.');
    });

    it('does not let a reload caused by another unit discard the requirements being changed', () => {
        fixture.componentRef.setInput('configuration', saved());
        fixture.detectChanges();
        state()['setRequirement']('locationRequired', false);

        fixture.componentRef.setInput('configuration', saved({ version: 4 }));
        fixture.detectChanges();

        expect(state()['draft'].locationRequired).toBeFalse();
        expect(state()['draft'].version).toBe(3);
    });

    it('saves the draft and hands the recalculated view to the page', () => {
        const view = { configuration: saved({ version: 4, locationRequired: false }) } as EventPreparationView;
        service.configure.and.returnValue(of(view));
        const emitted: EventPreparationView[] = [];
        component.saved.subscribe((value) => emitted.push(value));
        fixture.componentRef.setInput('configuration', saved());
        fixture.detectChanges();
        state()['setRequirement']('locationRequired', false);

        state()['save']();

        expect(service.configure).toHaveBeenCalledWith(42, jasmine.objectContaining({ locationRequired: false, version: 3 }), jasmine.anything());
        expect(service.configure.calls.mostRecent().args[2]!.get(HANDLED_ERRORS)).toEqual(['eventPreparation.versionConflict']);
        expect(emitted).toEqual([view]);
        expect(dirty).toEqual([true, false]);
        expect(state()['draft'].version).toBe(4);
    });

    it('offers to reload after a conflict and drops the draft only once accepted', () => {
        service.configure.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
        let conflicts = 0;
        component.conflict.subscribe(() => conflicts++);
        fixture.componentRef.setInput('configuration', saved());
        fixture.detectChanges();
        state()['setRequirement']('locationRequired', false);

        state()['save']();
        expect(conflicts).toBe(0);
        expect(state()['dirty']).toBeTrue();

        confirm.confirmReversible.calls.mostRecent().args[0].accept();
        expect(conflicts).toBe(1);
        expect(dirty).toEqual([true, false]);
    });

    it('ignores the errors the interceptor already reports', () => {
        service.configure.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        fixture.componentRef.setInput('configuration', saved());
        fixture.detectChanges();
        state()['setRequirement']('locationRequired', false);

        state()['save']();

        expect(confirm.confirmReversible).not.toHaveBeenCalled();
        expect(state()['dirty']).toBeTrue();
    });
});
