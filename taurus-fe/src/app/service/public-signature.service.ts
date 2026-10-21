import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable, of, tap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { SignatureAct, SignatureActOutcome, SignatureChainVerification, SignatureChallenge, SignatureChallengeRequest, SignatureConfirmation, SignatureDocumentRole, SignatureHolderData, SignatureLinkAct, SignatureMethods } from '../module';
import { cieSignForm, SignatureService } from './signature.service';

/** Percorso degli endpoint della pagina pubblica, che l'intercettore chiama senza token. */
export const PUBLIC_SIGNATURE_API = '/signature-links/v1/';

/**
 * Pagina pubblica di firma del genitore o tutore senza account: le operazioni di {@link SignatureService} sugli
 * endpoint del link, senza token. Il codice arriva sempre via e-mail all'indirizzo del link e l'atto è sempre quello
 * del link; in alternativa il genitore firma i documenti con CieSign. La pagina la fornisce al posto di SignatureService, così le finestre di firma la usano senza saperlo.
 */
@Injectable()
export class PublicSignatureService extends SignatureService {
    private readonly linkUrl = `${environment.baseUrl}${PUBLIC_SIGNATURE_API}`;
    private token = '';
    private maskedEmail?: string;

    constructor(private readonly client: HttpClient) {
        super(client);
    }

    /** Imposta il token del link aperto dalla pagina. */
    use(token: string): void {
        this.token = token;
    }

    getLinkAct(context?: HttpContext): Observable<SignatureLinkAct> {
        return this.client.get<SignatureLinkAct>(this.url(''), { context }).pipe(tap((value) => (this.maskedEmail = value.maskedEmail)));
    }

    /** Dalla pagina pubblica il codice arriva solo via e-mail. */
    override getMethods(): Observable<SignatureMethods> {
        return of({ enabled: true, preference: 'EMAIL_OTP', proposed: 'EMAIL_OTP', totpAvailable: false, totpConfigured: false, maskedEmail: this.maskedEmail });
    }

    override updatePreference(): Observable<SignatureMethods> {
        return throwError(() => new Error('The public page has no preferences'));
    }

    override createChallenge(request: SignatureChallengeRequest, context?: HttpContext): Observable<SignatureChallenge> {
        return this.client.post<SignatureChallenge>(this.url('/challenges'), { purpose: request.purpose, documentHash: request.items[0]?.documentHash }, { context });
    }

    override resend(id: number, context?: HttpContext): Observable<SignatureChallenge> {
        return this.client.post<SignatureChallenge>(this.url(`/challenges/${id}/resend`), null, { context });
    }

    override confirm(id: number, code: string, context?: HttpContext): Observable<SignatureConfirmation> {
        return this.client.post<SignatureConfirmation>(this.url(`/challenges/${id}/confirm`), { code }, { context });
    }

    override getActs(): Observable<SignatureAct[]> {
        return this.getLinkAct().pipe(map((value) => [value.act]));
    }

    override getAct(): Observable<SignatureAct> {
        return this.getLinkAct().pipe(map((value) => value.act));
    }

    override submitHolderData(_id: number, data: SignatureHolderData, context?: HttpContext): Observable<SignatureAct> {
        return this.client.put<SignatureLinkAct>(this.url('/holder-data'), data, { context }).pipe(map((value) => value.act));
    }

    override downloadDocument(_id: number, role: SignatureDocumentRole): Observable<Blob> {
        return this.client.get(this.url(`/documents/${role}`), { responseType: 'blob' });
    }

    override downloadAttestation(): Observable<Blob> {
        return this.client.get(this.url('/attestation'), { responseType: 'blob' });
    }

    override signWithCieSign(_id: number, role: SignatureDocumentRole, file: File, context?: HttpContext): Observable<SignatureActOutcome> {
        return this.client.post<SignatureActOutcome>(this.url(`/documents/${role}/ciesign`), cieSignForm(file), { context });
    }

    override verifyChain(): Observable<SignatureChainVerification> {
        return throwError(() => new Error('The public page cannot verify the chain'));
    }

    private url(path: string): string {
        return `${this.linkUrl}${encodeURIComponent(this.token)}${path}`;
    }
}
