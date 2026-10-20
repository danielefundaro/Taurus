import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { SignatureMethod, SignatureMethods } from '../../module';
import { SignatureService, ToastService, withErrorSummary } from '../../service';

/**
 * Sezione del profilo per la firma elettronica: metodo preferito per le conferme con codice e stato dell'app di
 * autenticazione, che si configura con l'azione di Keycloak. Compare solo se l'associazione usa la firma elettronica.
 */
@Component({
    selector: 'app-signature-method-settings',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './signature-method-settings.component.html'
})
export class SignatureMethodSettingsComponent implements OnInit {
    /** Avvia la configurazione dell'app di autenticazione in Keycloak. */
    @Output() configureApp = new EventEmitter<void>();

    protected methods?: SignatureMethods;
    protected preference: SignatureMethod = 'EMAIL_OTP';
    protected saving = false;
    protected readonly options: { label: string; value: SignatureMethod }[] = [
        { label: 'Codice via e-mail', value: 'EMAIL_OTP' },
        { label: 'App di autenticazione', value: 'TOTP' }
    ];

    constructor(
        private readonly signatureService: SignatureService,
        private readonly toastService: ToastService
    ) {}

    ngOnInit(): void {
        this.signatureService
            .getMethods()
            .pipe(first())
            .subscribe((methods) => this.apply(methods));
    }

    /** L'app è preferita ma non si può usare: le firme useranno il codice via e-mail. */
    protected get fallsBackToEmail(): boolean {
        return !!this.methods && this.methods.preference === 'TOTP' && this.methods.proposed !== 'TOTP';
    }

    /** Salva subito il metodo scelto; in caso di errore torna a quello salvato. */
    protected save(preference: SignatureMethod): void {
        if (!this.methods || this.saving || preference === this.methods.preference) return;
        this.preference = preference;
        this.saving = true;
        this.signatureService
            .updatePreference(preference, withErrorSummary('Metodo non salvato'))
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (methods) => {
                    this.apply(methods);
                    this.toastService.success('Metodo salvato', preference === 'TOTP' ? 'Le firme useranno l’app di autenticazione.' : 'Le firme useranno il codice via e-mail.');
                },
                error: () => (this.preference = this.methods!.preference)
            });
    }

    private apply(methods: SignatureMethods): void {
        this.methods = methods;
        this.preference = methods.preference;
    }
}
