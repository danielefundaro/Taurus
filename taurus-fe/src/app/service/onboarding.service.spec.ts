import { HttpResponse, provideHttpClient } from '@angular/common/http';
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

    it('sends the validator of the previous read and keeps the one answered', () => {
        let state: { job?: unknown; etag?: string } | undefined;
        service.job(42, '"42-VALIDATING-VALIDATING_ROWS-10-0-0"').subscribe((result) => (state = result));
        const request = http.expectOne(`${environment.baseUrl}/onboarding/imports/42`);

        expect(request.request.headers.get('If-None-Match')).toBe('"42-VALIDATING-VALIDATING_ROWS-10-0-0"');

        request.flush({ id: 42 }, { headers: { ETag: '"42-READY-VALIDATION_COMPLETED-100-2-0"' } });

        expect(state?.job).toEqual({ id: 42 });
        expect(state?.etag).toBe('"42-READY-VALIDATION_COMPLETED-100-2-0"');
    });

    it('answers a «not modified» read with no job and the validator already held', () => {
        let state: { job?: unknown; etag?: string } | undefined;
        service.job(42, '"42-VALIDATING-VALIDATING_ROWS-10-0-0"').subscribe((result) => (state = result));

        // What the interceptor delivers for a 304: the empty answer of a conditional read, not an error.
        http.expectOne(`${environment.baseUrl}/onboarding/imports/42`).event(new HttpResponse<any>({ status: 304 }));

        expect(state?.job).toBeUndefined();
        expect(state?.etag).toBe('"42-VALIDATING-VALIDATING_ROWS-10-0-0"');
    });

    it('reads the outcome of a finished import', () => {
        service.outcome(42).subscribe();
        const request = http.expectOne(`${environment.baseUrl}/onboarding/imports/42/outcome`);

        expect(request.request.method).toBe('GET');

        request.flush({ status: 'COMPLETED', identitiesCreated: 2, identitiesLinked: 1, setupEmailFailures: 0, sections: [] });
    });

    it('uses a distinct idempotency key when applying', () => {
        service.apply(42, true, false, 'apply-key').subscribe();
        const request = http.expectOne(`${environment.baseUrl}/onboarding/imports/42/apply`);
        expect(request.request.headers.get('Idempotency-Key')).toBe('apply-key');
        expect(request.request.body).toEqual({ warningsAccepted: true, sendSetupEmails: false });
        request.flush({});
    });
});
