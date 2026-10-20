import { HttpContext, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { SignatureChallengeRequest } from '../module';
import { ERROR_SUMMARY, withErrorSummary } from './api-error-context';
import { SignatureService } from './signature.service';

describe('SignatureService', () => {
    let service: SignatureService;
    let http: HttpTestingController;
    const personal = `${environment.baseUrl}/user/signatures`;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(SignatureService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());

    it('reads the methods of the authenticated user', () => {
        service.getMethods().subscribe((methods) => expect(methods.enabled).toBeTrue());
        const request = http.expectOne(`${personal}/methods`);
        expect(request.request.method).toBe('GET');
        request.flush({ enabled: true, preference: 'EMAIL_OTP', proposed: 'EMAIL_OTP', totpAvailable: true, totpConfigured: false, maskedEmail: 'm***@example.org' });
    });

    it('saves the preferred method', () => {
        service.updatePreference('TOTP').subscribe((methods) => expect(methods.preference).toBe('TOTP'));
        const request = http.expectOne(`${personal}/methods`);
        expect(request.request.method).toBe('PUT');
        expect(request.request.body).toEqual({ preference: 'TOTP' });
        request.flush({ enabled: true, preference: 'TOTP', proposed: 'EMAIL_OTP', totpAvailable: true, totpConfigured: false });
    });

    it('opens a challenge with the confirmed items and keeps the declared error context', () => {
        const body: SignatureChallengeRequest = { purpose: 'ASSIGNMENT_DECISION', method: 'EMAIL_OTP', items: [{ assignmentId: 3, revisionHash: 'a'.repeat(64), decision: 'ACCEPTED' }] };

        service.createChallenge(body, withErrorSummary('Codice non inviato')).subscribe();
        const request = http.expectOne(`${personal}/challenges`);
        expect(request.request.method).toBe('POST');
        expect(request.request.body).toEqual(body);
        expect(request.request.context.get(ERROR_SUMMARY)).toBe('Codice non inviato');
        request.flush({ id: 9 });
    });

    it('sends a new code and confirms the challenge with the code', () => {
        service.resend(9, new HttpContext()).subscribe();
        const resend = http.expectOne(`${personal}/challenges/9/resend`);
        expect(resend.request.method).toBe('POST');
        resend.flush({ id: 9 });

        service.confirm(9, '123456').subscribe((confirmation) => expect(confirmation.acts.length).toBe(1));
        const confirm = http.expectOne(`${personal}/challenges/9/confirm`);
        expect(confirm.request.method).toBe('POST');
        expect(confirm.request.body).toEqual({ code: '123456' });
        confirm.flush({ challengeId: 9, acts: [{ actId: 1, type: 'ASSIGNMENT_ACKNOWLEDGMENT', status: 'SIGNED', assignmentId: 3 }] });
    });

    it('asks the administrators endpoint to verify the evidence chain', () => {
        service.verifyChain().subscribe((result) => expect(result.valid).toBeTrue());
        const request = http.expectOne(`${environment.baseUrl}/inventory/signatures/verify-chain`);
        expect(request.request.method).toBe('POST');
        request.flush({ valid: true, checked: 4 });
    });
});
