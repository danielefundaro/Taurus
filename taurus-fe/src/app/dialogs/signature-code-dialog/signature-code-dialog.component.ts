import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { InputOtpModule } from 'primeng/inputotp';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { SignatureChallenge, SignatureCodeRequest, SignatureConfirmation, SignatureMethod, SignatureMethods } from '../../module';
import { ApiErrorService, SignatureService, withErrorSummary, withHandledErrors } from '../../service';

export interface SignatureCodeDialogData {
    heading: string;
    subtitle?: string;
    request: SignatureCodeRequest;
}

/** Esito della finestra: la conferma registrata, oppure la richiesta di ricaricare i dati dopo un errore. */
export interface SignatureCodeDialogResult {
    confirmation?: SignatureConfirmation;
    reload?: boolean;
}

/** Errori della conferma mostrati nella finestra invece che in un avviso. */
const CHALLENGE_ERRORS = ['signature.challenge.*', 'signature.method.*'];
/** Errori che chiudono la conferma: serve un nuovo codice. */
const RESTART_ERRORS = ['signature.challenge.expired', 'signature.challenge.locked', 'signature.challenge.closed', 'signature.challenge.limit', 'signature.challenge.notFound'];
/** Errori per cui il metodo scelto non si può usare: la finestra propone l'altro. */
const METHOD_ERRORS = ['signature.method.*'];

/**
 * Firma con codice: con il metodo proposto dal profilo invia il codice all'indirizzo e-mail dell'utente oppure chiede
 * quello dell'app di autenticazione, mostra le dichiarazioni confermate e registra la firma quando l'utente inserisce il
 * codice. L'utente può sempre passare all'altro metodo disponibile.
 */
@Component({
    selector: 'app-signature-code-dialog',
    standalone: true,
    imports: [ImportsModule, InputOtpModule],
    templateUrl: './signature-code-dialog.component.html',
    styleUrl: './signature-code-dialog.component.scss'
})
export class SignatureCodeDialogComponent implements OnInit, OnDestroy {
    protected readonly data: SignatureCodeDialogData;
    protected methods?: SignatureMethods;
    protected method: SignatureMethod = 'EMAIL_OTP';
    protected challenge?: SignatureChallenge;
    protected code = '';
    protected sending = false;
    protected confirming = false;
    protected problem?: string;
    protected restartNeeded = false;
    /** Il metodo scelto non si può usare in questo momento: la finestra propone l'altro. */
    protected methodProblem = false;
    protected now = Date.now();
    private timer?: ReturnType<typeof setInterval>;

    constructor(
        private readonly dialogRef: DynamicDialogRef<SignatureCodeDialogComponent>,
        private readonly signatureService: SignatureService,
        private readonly apiErrorService: ApiErrorService,
        config: DynamicDialogConfig<SignatureCodeDialogData>
    ) {
        this.data = config.data!;
    }

    ngOnInit(): void {
        this.timer = setInterval(() => (this.now = Date.now()), 1000);
        this.sending = true;
        this.signatureService
            .getMethods()
            .pipe(first())
            .subscribe({
                next: (methods) => {
                    this.methods = methods;
                    this.method = methods.proposed;
                    this.requestCode();
                },
                error: () => this.dialogRef.close({ reload: true })
            });
    }

    ngOnDestroy(): void {
        if (this.timer) clearInterval(this.timer);
    }

    protected get codeComplete(): boolean {
        return /^\d{6}$/.test(this.code ?? '');
    }

    /** Metodo alternativo offerto dalla finestra: l'app solo se configurata e verificabile. */
    protected get otherMethod(): SignatureMethod | undefined {
        if (this.method === 'TOTP') return 'EMAIL_OTP';
        return this.methods?.totpAvailable && this.methods.totpConfigured ? 'TOTP' : undefined;
    }

    protected get otherMethodLabel(): string {
        return this.otherMethod === 'TOTP' ? 'Usa l’app di autenticazione' : 'Ricevi il codice via e-mail';
    }

    /** Secondi mancanti al prossimo invio possibile; zero quando un nuovo codice si può già chiedere. */
    protected get resendWait(): number {
        if (!this.challenge?.resendAvailableAt) return 0;
        return Math.max(0, Math.ceil((new Date(this.challenge.resendAvailableAt).getTime() - this.now) / 1000));
    }

    protected get canResend(): boolean {
        return !!this.challenge && this.challenge.method === 'EMAIL_OTP' && !this.sending && !this.restartNeeded && this.challenge.remainingSends > 0 && this.resendWait === 0;
    }

    /**
     * Apre una nuova conferma con il metodo scelto: via e-mail invia il codice. Se il metodo non si può usare la finestra lo
     * spiega e propone l'altro; ogni altro errore chiude la finestra con l'avviso dell'intercettore.
     */
    protected requestCode(): void {
        this.sending = true;
        this.problem = undefined;
        this.restartNeeded = false;
        this.methodProblem = false;
        this.code = '';
        this.signatureService
            .createChallenge({ ...this.data.request, method: this.method }, withHandledErrors(METHOD_ERRORS, withErrorSummary(this.method === 'TOTP' ? 'Firma non avviata' : 'Codice non inviato')))
            .pipe(
                first(),
                finalize(() => (this.sending = false))
            )
            .subscribe({
                next: (challenge) => (this.challenge = challenge),
                error: (error: HttpErrorResponse) => {
                    const message = this.apiErrorService.handledMessage(error, METHOD_ERRORS);
                    if (!message) {
                        this.dialogRef.close({ reload: true });
                        return;
                    }
                    this.challenge = undefined;
                    this.problem = message;
                    this.methodProblem = true;
                }
            });
    }

    /** Passa all'altro metodo con una nuova conferma; quella aperta scade da sola. */
    protected switchMethod(): void {
        const other = this.otherMethod;
        if (!other || this.sending || this.confirming) return;
        this.method = other;
        this.challenge = undefined;
        this.requestCode();
    }

    protected resend(): void {
        if (!this.challenge || !this.canResend) return;
        this.sending = true;
        this.problem = undefined;
        this.signatureService
            .resend(this.challenge.id, withHandledErrors(CHALLENGE_ERRORS, withErrorSummary('Codice non inviato')))
            .pipe(
                first(),
                finalize(() => (this.sending = false))
            )
            .subscribe({
                next: (challenge) => {
                    this.challenge = challenge;
                    this.code = '';
                },
                error: (error: HttpErrorResponse) => this.showProblem(error)
            });
    }

    protected confirm(): void {
        if (!this.challenge || !this.codeComplete || this.confirming || this.restartNeeded) return;
        this.confirming = true;
        this.problem = undefined;
        this.signatureService
            .confirm(this.challenge.id, this.code, withHandledErrors(CHALLENGE_ERRORS, withErrorSummary('Firma non registrata')))
            .pipe(
                first(),
                finalize(() => (this.confirming = false))
            )
            .subscribe({
                next: (confirmation) => this.dialogRef.close({ confirmation }),
                error: (error: HttpErrorResponse) => {
                    if (!this.showProblem(error)) this.dialogRef.close({ reload: true });
                }
            });
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    /** Mostra nella finestra un errore della conferma; restituisce `false` per gli altri errori, già notificati. */
    private showProblem(error: HttpErrorResponse): boolean {
        const message = this.apiErrorService.handledMessage(error, CHALLENGE_ERRORS);
        if (!message) return false;
        this.problem = message;
        this.code = '';
        if (RESTART_ERRORS.includes(this.apiErrorService.resolve(error).key)) this.restartNeeded = true;
        return true;
    }
}
