import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LegalAcceptanceRequest, LegalDocument, LegalDocumentType, LegalPreview, LegalStatus, PublicLegalDocument } from '../module';

/** Percorso dell'API pubblica dei documenti legali, che l'intercettore chiama senza token. */
export const PUBLIC_LEGAL_API = '/legal-documents/v1/';

/** Indirizzo pubblico di un documento nell'applicativo: la versione in vigore, o una versione precisa. */
export const legalDocumentPath = (type: LegalDocumentType, version?: string): string => `/legal/${type.toLowerCase()}${version ? `/${encodeURIComponent(version)}` : ''}`;

@Injectable({ providedIn: 'root' })
export class LegalService {
    private readonly baseUrl = `${environment.baseUrl}/legal`;
    private readonly publicUrl = `${environment.baseUrl}${PUBLIC_LEGAL_API}`;
    private status$?: Observable<LegalStatus>;

    constructor(private readonly http: HttpClient) { }

    getStatus(forceRefresh = false): Observable<LegalStatus> {
        if (!this.status$ || forceRefresh) {
            this.status$ = this.http.get<LegalStatus>(`${this.baseUrl}/status`).pipe(shareReplay({ bufferSize: 1, refCount: false }));
        }
        return this.status$;
    }

    accept(documentIds: number[]): Observable<LegalStatus> {
        const request: LegalAcceptanceRequest = { documentIds };
        return this.http.post<LegalStatus>(`${this.baseUrl}/acceptances`, request).pipe(tap((status) => (this.status$ = of(status).pipe(shareReplay({ bufferSize: 1, refCount: false })))));
    }

    /** Una versione pubblicata, senza login: quella in vigore se la versione manca. */
    getPublicDocument(type: LegalDocumentType, version?: string, context?: HttpContext): Observable<PublicLegalDocument> {
        const path = `${type.toLowerCase()}${version ? `/${encodeURIComponent(version)}` : ''}`;
        return this.http.get<PublicLegalDocument>(`${this.publicUrl}${path}`, { context });
    }

    getDocuments(context?: HttpContext): Observable<LegalDocument[]> {
        return this.http.get<LegalDocument[]>(`${this.baseUrl}/documents`, { context });
    }

    getDocument(id: number, context?: HttpContext): Observable<LegalDocument> {
        return this.http.get<LegalDocument>(`${this.baseUrl}/documents/${id}`, { context });
    }

    createDocument(document: LegalDocument, context?: HttpContext): Observable<LegalDocument> {
        return this.http.post<LegalDocument>(`${this.baseUrl}/documents`, document, { context }).pipe(tap(() => this.clearCache()));
    }

    updateDocument(id: number, document: LegalDocument, context?: HttpContext): Observable<LegalDocument> {
        return this.http.put<LegalDocument>(`${this.baseUrl}/documents/${id}`, document, { context }).pipe(tap(() => this.clearCache()));
    }

    deleteDocument(id: number, context?: HttpContext): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/documents/${id}`, { context }).pipe(tap(() => this.clearCache()));
    }

    /** Pubblica una bozza o riattiva una versione ritirata; la versione in vigore dello stesso tipo si ritira. */
    activateDocument(id: number, context?: HttpContext): Observable<LegalDocument> {
        return this.http.post<LegalDocument>(`${this.baseUrl}/documents/${id}/activation`, null, { context }).pipe(tap(() => this.clearCache()));
    }

    withdrawDocument(id: number, context?: HttpContext): Observable<LegalDocument> {
        return this.http.delete<LegalDocument>(`${this.baseUrl}/documents/${id}/activation`, { context }).pipe(tap(() => this.clearCache()));
    }

    /** Il testo come lo vedranno gli utenti, convertito dallo stesso convertitore della pagina pubblica. */
    preview(content: string, context?: HttpContext): Observable<string> {
        return this.http.post<LegalPreview>(`${this.baseUrl}/documents/preview`, { content }, { context }).pipe(map((preview) => preview.html));
    }

    clearCache(): void {
        this.status$ = undefined;
    }
}
