import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { OnboardingService } from './onboarding.service';

describe('OnboardingService', () => {
    let service: OnboardingService;
    let http: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(OnboardingService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());

    it('uploads a workbook with its idempotency key and selected sections', () => {
        const file = new File(['xlsx'], 'tenant.xlsx');
        service.upload(file, 'XLSX', undefined, ['INSTRUMENTS', 'USERS'], 'upload-key').subscribe();
        const request = http.expectOne((candidate) => candidate.url === `${environment.baseUrl}/onboarding/imports`);
        expect(request.request.method).toBe('POST');
        expect(request.request.headers.get('Idempotency-Key')).toBe('upload-key');
        expect(request.request.params.getAll('selectedSections')).toEqual(['INSTRUMENTS', 'USERS']);
        expect(request.request.body instanceof FormData).toBeTrue();
        request.flush({});
    });

    it('uses a distinct idempotency key when applying', () => {
        service.apply(42, true, false, 'apply-key').subscribe();
        const request = http.expectOne(`${environment.baseUrl}/onboarding/imports/42/apply`);
        expect(request.request.headers.get('Idempotency-Key')).toBe('apply-key');
        expect(request.request.body).toEqual({ warningsAccepted: true, sendSetupEmails: false });
        request.flush({});
    });
});
