import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { SignatureMethods } from '../../module';
import { SignatureService, ToastService } from '../../service';
import { SignatureMethodSettingsComponent } from './signature-method-settings.component';

describe('SignatureMethodSettingsComponent', () => {
    const methods: SignatureMethods = { enabled: true, preference: 'EMAIL_OTP', proposed: 'EMAIL_OTP', totpAvailable: true, totpConfigured: false, maskedEmail: 'm***@example.org' };

    function create(initial: SignatureMethods): { component: SignatureMethodSettingsComponent; signatureService: jasmine.SpyObj<SignatureService>; toastService: jasmine.SpyObj<ToastService> } {
        const signatureService = jasmine.createSpyObj<SignatureService>('SignatureService', ['getMethods', 'updatePreference']);
        signatureService.getMethods.and.returnValue(of(initial));
        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success']);
        const component = new SignatureMethodSettingsComponent(signatureService, toastService);
        component.ngOnInit();
        return { component, signatureService, toastService };
    }

    it('saves the preferred method and says when the signatures still use the e-mail code', () => {
        const { component, signatureService, toastService } = create(methods);
        const saved: SignatureMethods = { ...methods, preference: 'TOTP' };
        signatureService.updatePreference.and.returnValue(of(saved));

        component['save']('EMAIL_OTP');
        expect(signatureService.updatePreference).not.toHaveBeenCalled();

        component['save']('TOTP');

        expect(signatureService.updatePreference).toHaveBeenCalledWith('TOTP', jasmine.any(HttpContext));
        expect(component['preference']).toBe('TOTP');
        expect(component['fallsBackToEmail']).toBeTrue();
        expect(toastService.success).toHaveBeenCalled();
    });

    it('does not warn when the configured app is proposed', () => {
        const { component } = create({ ...methods, preference: 'TOTP', proposed: 'TOTP', totpConfigured: true });

        expect(component['fallsBackToEmail']).toBeFalse();
    });

    it('goes back to the saved method when saving fails', () => {
        const { component, signatureService } = create(methods);
        signatureService.updatePreference.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        component['save']('TOTP');

        expect(component['preference']).toBe('EMAIL_OTP');
        expect(component['saving']).toBeFalse();
    });
});
