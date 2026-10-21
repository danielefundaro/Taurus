import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { SignatureAct, SignatureActOutcome, SignatureChainVerification, SignatureChallenge, SignatureChallengeRequest, SignatureConfirmation, SignatureDocumentRole, SignatureHolderData, SignatureMethod, SignatureMethods } from '../module';

/**
 * Firma elettronica dell'utente autenticato: metodi disponibili, conferme con codice e documenti firmati con CieSign,
 * più la verifica della catena delle prove riservata agli amministratori.
 */
@Injectable({ providedIn: 'root' })
export class SignatureService {
    private readonly userUrl = `${environment.baseUrl}/user/signatures`;
    private readonly adminUrl = `${environment.baseUrl}/inventory/signatures`;

    constructor(private readonly http: HttpClient) {}

    getMethods(): Observable<SignatureMethods> {
        return this.http.get<SignatureMethods>(`${this.userUrl}/methods`);
    }

    /** Salva il metodo preferito; l'app di autenticazione è proposta solo una volta configurata. */
    updatePreference(preference: SignatureMethod, context?: HttpContext): Observable<SignatureMethods> {
        return this.http.put<SignatureMethods>(`${this.userUrl}/methods`, { preference }, { context });
    }

    createChallenge(request: SignatureChallengeRequest, context?: HttpContext): Observable<SignatureChallenge> {
        return this.http.post<SignatureChallenge>(`${this.userUrl}/challenges`, request, { context });
    }

    resend(id: number, context?: HttpContext): Observable<SignatureChallenge> {
        return this.http.post<SignatureChallenge>(`${this.userUrl}/challenges/${id}/resend`, null, { context });
    }

    confirm(id: number, code: string, context?: HttpContext): Observable<SignatureConfirmation> {
        return this.http.post<SignatureConfirmation>(`${this.userUrl}/challenges/${id}/confirm`, { code }, { context });
    }

    /** Atti dei fogli di affidamento in cui l'utente firma, ancora in Taurus. */
    getActs(): Observable<SignatureAct[]> {
        return this.http.get<SignatureAct[]>(`${this.userUrl}/acts`);
    }

    getAct(id: number): Observable<SignatureAct> {
        return this.http.get<SignatureAct>(`${this.userUrl}/acts/${id}`);
    }

    submitHolderData(id: number, data: SignatureHolderData, context?: HttpContext): Observable<SignatureAct> {
        return this.http.put<SignatureAct>(`${this.userUrl}/acts/${id}/holder-data`, data, { context });
    }

    downloadDocument(id: number, role: SignatureDocumentRole): Observable<Blob> {
        return this.http.get(`${this.userUrl}/acts/${id}/documents/${role}`, { responseType: 'blob' });
    }

    downloadAttestation(id: number): Observable<Blob> {
        return this.http.get(`${this.userUrl}/acts/${id}/attestation`, { responseType: 'blob' });
    }

    /** Carica il documento firmato con CieSign, che registra la firma dell'Affidatario al posto del codice. */
    signWithCieSign(id: number, role: SignatureDocumentRole, file: File, context?: HttpContext): Observable<SignatureActOutcome> {
        return this.http.post<SignatureActOutcome>(`${this.userUrl}/acts/${id}/documents/${role}/ciesign`, cieSignForm(file), { context });
    }

    verifyChain(): Observable<SignatureChainVerification> {
        return this.http.post<SignatureChainVerification>(`${this.adminUrl}/verify-chain`, null);
    }
}

/** Corpo multipart del caricamento CieSign. */
export function cieSignForm(file: File): FormData {
    const form = new FormData();
    form.append('file', file, file.name);
    return form;
}
