import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LegalAcceptanceRequest, LegalDocument, LegalStatus } from '../module';

@Injectable({ providedIn: 'root' })
export class LegalService {
    private readonly baseUrl = `${environment.baseUrl}/legal`;
    private status$?: Observable<LegalStatus>;

    constructor(private readonly http: HttpClient) {}

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

    getDocuments(): Observable<LegalDocument[]> {
        return this.http.get<LegalDocument[]>(`${this.baseUrl}/documents`);
    }

    createDocument(document: LegalDocument): Observable<LegalDocument> {
        return this.http.post<LegalDocument>(`${this.baseUrl}/documents`, document).pipe(tap(() => this.clearCache()));
    }

    updateDocument(id: number, document: LegalDocument): Observable<LegalDocument> {
        return this.http.put<LegalDocument>(`${this.baseUrl}/documents/${id}`, document).pipe(tap(() => this.clearCache()));
    }

    clearCache(): void {
        this.status$ = undefined;
    }
}
