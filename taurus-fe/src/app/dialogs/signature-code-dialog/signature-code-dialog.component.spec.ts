import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Observable, of, throwError } from 'rxjs';
import { SignatureChallenge, SignatureMethods } from '../../module';
import { ApiErrorService, SignatureService } from '../../service';
import { SignatureCodeDialogComponent, SignatureCodeDialogData } from './signature-code-dialog.component';

describe('SignatureCodeDialogComponent', () => {
    const data: SignatureCodeDialogData = {
        heading: 'Firma la presa visione',
        request: { purpose: 'ASSIGNMENT_DECISION', items: [{ assignmentId: 3, revisionHash: 'a'.repeat(64), decision: 'ACCEPTED' }] }
    };
    const challenge: SignatureChallenge = {
        id: 9,
        purpose: 'ASSIGNMENT_DECISION',
        method: 'EMAIL_OTP',
        maskedEmail: 'm***@example.org',
        reference: 'ABCD-1234',
        expiresAt: '2026-10-19T10:10:00Z',
        resendAvailableAt: '2026-10-19T10:01:00Z',
        remainingSends: 4,
        remainingAttempts: 5,
        declarations: ['Accetto la consegna di Clarinetto (INV-1).']
    };
    const appChallenge: SignatureChallenge = { ...challenge, id: 11, method: 'TOTP', maskedEmail: undefined, resendAvailableAt: undefined, remainingSends: 0 };
    const emailMethods: SignatureMethods = { enabled: true, preference: 'EMAIL_OTP', proposed: 'EMAIL_OTP', totpAvailable: true, totpConfigured: false, maskedEmail: 'm***@example.org' };
    const appMethods: SignatureMethods = { ...emailMethods, preference: 'TOTP', proposed: 'TOTP', totpConfigured: true };
    let component: SignatureCodeDialogComponent;
    let dialogRef: jasmine.SpyObj<DynamicDialogRef<SignatureCodeDialogComponent>>;
    let signatureService: jasmine.SpyObj<SignatureService>;

    function failure(status: number, key: string, args: Record<string, unknown> = {}): HttpErrorResponse {
        return new HttpErrorResponse({ status, error: { message: `error.${key}`, args } });
    }

    function open(methods: SignatureMethods, first: Observable<SignatureChallenge> = of(challenge)): void {
        component?.ngOnDestroy();
        signatureService.getMethods.and.returnValue(of(methods));
        signatureService.createChallenge.and.returnValue(first);
        const config = new DynamicDialogConfig<SignatureCodeDialogData>();
        config.data = data;
        component = new SignatureCodeDialogComponent(dialogRef, signatureService, TestBed.inject(ApiErrorService), config);
        component.ngOnInit();
    }

    beforeEach(() => {
        dialogRef = jasmine.createSpyObj<DynamicDialogRef<SignatureCodeDialogComponent>>('DynamicDialogRef', ['close']);
        signatureService = jasmine.createSpyObj<SignatureService>('SignatureService', ['getMethods', 'createChallenge', 'resend', 'confirm']);
        open(emailMethods);
    });
    afterEach(() => component.ngOnDestroy());

    it('sends the code when it opens and closes with the confirmation of the right code', () => {
        expect(signatureService.createChallenge).toHaveBeenCalledWith({ ...data.request, method: 'EMAIL_OTP' }, jasmine.any(HttpContext));
        expect(component['challenge']).toEqual(challenge);
        expect(component['otherMethod']).toBeUndefined();
        const confirmation = { challengeId: 9, acts: [{ actId: 1, type: 'ASSIGNMENT_ACKNOWLEDGMENT' as const, status: 'SIGNED' as const, assignmentId: 3 }] };
        signatureService.confirm.and.returnValue(of(confirmation));

        component['confirm']();
        expect(signatureService.confirm).not.toHaveBeenCalled();

        component['code'] = '123456';
        component['confirm']();

        expect(signatureService.confirm).toHaveBeenCalledWith(9, '123456', jasmine.any(HttpContext));
        expect(dialogRef.close).toHaveBeenCalledWith({ confirmation });
    });

    it('proposes the authentication app when the profile prefers it and offers the e-mail code', () => {
        open(appMethods, of(appChallenge));

        expect(signatureService.createChallenge).toHaveBeenCalledWith({ ...data.request, method: 'TOTP' }, jasmine.any(HttpContext));
        expect(component['canResend']).toBeFalse();
        expect(component['otherMethod']).toBe('EMAIL_OTP');
        expect(component['otherMethodLabel']).toBe('Ricevi il codice via e-mail');

        signatureService.createChallenge.and.returnValue(of(challenge));
        component['switchMethod']();

        expect(signatureService.createChallenge).toHaveBeenCalledWith({ ...data.request, method: 'EMAIL_OTP' }, jasmine.any(HttpContext));
        expect(component['challenge']).toEqual(challenge);
        expect(component['otherMethod']).toBe('TOTP');
    });

    it('offers the other method when the authentication app cannot be used', () => {
        open(
            appMethods,
            throwError(() => failure(502, 'signature.method.unavailable'))
        );

        expect(component['methodProblem']).toBeTrue();
        expect(component['problem']).toBe("L'app di autenticazione non si può verificare in questo momento: usare il codice via e-mail");
        expect(dialogRef.close).not.toHaveBeenCalled();

        signatureService.createChallenge.and.returnValue(of(challenge));
        component['switchMethod']();

        expect(component['methodProblem']).toBeFalse();
        expect(component['challenge']).toEqual(challenge);
    });

    it('keeps the dialog open when Keycloak does not answer to the code of the app', () => {
        open(appMethods, of(appChallenge));
        signatureService.confirm.and.returnValue(throwError(() => failure(502, 'signature.method.unavailable')));

        component['code'] = '123456';
        component['confirm']();

        expect(component['problem']).toContain('usare il codice via e-mail');
        expect(component['restartNeeded']).toBeFalse();
        expect(dialogRef.close).not.toHaveBeenCalled();
    });

    it('shows a wrong code in the dialog and lets the user try again', () => {
        signatureService.confirm.and.returnValue(throwError(() => failure(422, 'signature.challenge.invalidCode', { remainingAttempts: 3 })));

        component['code'] = '000000';
        component['confirm']();

        expect(component['problem']).toBe('Codice errato: restano 3 tentativi');
        expect(component['code']).toBe('');
        expect(component['restartNeeded']).toBeFalse();
        expect(dialogRef.close).not.toHaveBeenCalled();
    });

    it('asks for a new code when the challenge is closed and starts a new one', () => {
        signatureService.confirm.and.returnValue(throwError(() => failure(410, 'signature.challenge.expired')));

        component['code'] = '123456';
        component['confirm']();

        expect(component['restartNeeded']).toBeTrue();
        expect(component['problem']).toBe('Il codice è scaduto: richiederne uno nuovo');

        signatureService.createChallenge.and.returnValue(of({ ...challenge, id: 10 }));
        component['requestCode']();

        expect(component['challenge']?.id).toBe(10);
        expect(component['restartNeeded']).toBeFalse();
        expect(component['problem']).toBeUndefined();
    });

    it('closes asking to reload when the data changed or the code cannot be sent', () => {
        signatureService.confirm.and.returnValue(throwError(() => failure(409, 'inventory.revision.versionConflict')));
        component['code'] = '123456';
        component['confirm']();
        expect(dialogRef.close).toHaveBeenCalledWith({ reload: true });

        dialogRef.close.calls.reset();
        signatureService.createChallenge.and.returnValue(throwError(() => failure(502, 'signature.mail.sendFailed')));
        component['requestCode']();
        expect(dialogRef.close).toHaveBeenCalledWith({ reload: true });
    });

    it('allows a new code only after the waiting time', () => {
        signatureService.resend.and.returnValue(of({ ...challenge, remainingSends: 3 }));
        component['now'] = new Date('2026-10-19T10:00:30Z').getTime();

        expect(component['resendWait']).toBe(30);
        expect(component['canResend']).toBeFalse();
        component['resend']();
        expect(signatureService.resend).not.toHaveBeenCalled();

        component['now'] = new Date('2026-10-19T10:01:00Z').getTime();
        component['code'] = '12';
        component['resend']();

        expect(signatureService.resend).toHaveBeenCalledWith(9, jasmine.any(HttpContext));
        expect(component['challenge']?.remainingSends).toBe(3);
        expect(component['code']).toBe('');
    });
});
