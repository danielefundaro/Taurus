import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { RoleEnums } from '../constants';
import { EventPreparationConfiguration } from '../module';
import { EventPreparationService } from './event-preparation.service';

describe('EventPreparationService', () => {
    let service: EventPreparationService;
    let http: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(EventPreparationService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());

    it('loads the preparation view from the endpoint dedicated to each role', () => {
        const cases = [
            [RoleEnums.ADMIN, `${environment.baseUrl}/calendar-events/42/preparation`],
            [RoleEnums.ARCHIVIST, `${environment.baseUrl}/calendar-events/42/preparation/catalogue`],
            [RoleEnums.USER, `${environment.baseUrl}/user/calendar-events/42/preparation`],
            [RoleEnums.USER_EXTERNAL, `${environment.baseUrl}/external/calendar-events/42/preparation`],
            [RoleEnums.TREASURER, `${environment.baseUrl}/finance/events/42/preparation`]
        ] as const;

        cases.forEach(([role, url]) => {
            service.get(42, role).subscribe();
            const request = http.expectOne(url);
            expect(request.request.method).toBe('GET');
            request.flush({});
        });
    });

    it('saves configuration as an independent unit', () => {
        const configuration: EventPreparationConfiguration = {
            profile: 'OTHER',
            locationRequired: true,
            programRequired: false,
            scoresRequired: false,
            availabilityRequired: false,
            availabilityDeadlineMinutes: 1440,
            materialsRequired: false,
            materialsDeadlineMinutes: 180,
            budgetRequired: false,
            presenceClosureRequired: false,
            financialClosureRequired: false,
            version: 2
        };
        service.configure(42, configuration).subscribe();
        const request = http.expectOne(`${environment.baseUrl}/calendar-events/42/preparation/configuration`);
        expect(request.request.method).toBe('PUT');
        expect(request.request.body).toEqual(configuration);
        request.flush({});
    });

    it('keeps program and material writes on separate endpoints', () => {
        service.replaceProgram(42, [{ trackId: 7, plannedDurationSeconds: 180 }]).subscribe();
        const program = http.expectOne(`${environment.baseUrl}/calendar-events/42/preparation/program`);
        expect(program.request.body).toEqual({ entries: [{ trackId: 7, plannedDurationSeconds: 180 }] });
        program.flush({});
        service.replaceMaterials(42, [{ itemId: 9, assignmentId: 11, requiredQuantity: 2 }]).subscribe();
        const materials = http.expectOne(`${environment.baseUrl}/calendar-events/42/preparation/materials`);
        expect(materials.request.body).toEqual({ materials: [{ itemId: 9, assignmentId: 11, requiredQuantity: 2 }] });
        materials.flush({});
    });

    it('keeps treasurer confirmations inside the finance API', () => {
        service.confirmBudget(42, RoleEnums.TREASURER).subscribe();
        const budget = http.expectOne(`${environment.baseUrl}/finance/events/42/budget-confirmation`);
        expect(budget.request.method).toBe('POST');
        budget.flush({});

        service.confirmNoMovements(42, RoleEnums.TREASURER).subscribe();
        const noMovements = http.expectOne(`${environment.baseUrl}/finance/events/42/no-movements-confirmation`);
        expect(noMovements.request.method).toBe('POST');
        noMovements.flush({});
    });
});
