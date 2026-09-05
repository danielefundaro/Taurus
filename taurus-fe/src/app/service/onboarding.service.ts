import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { OnboardingContext, OnboardingFormat, OnboardingIssue, OnboardingJob, OnboardingPage, OnboardingRow, OnboardingRowStatus, OnboardingSection, OnboardingSectionSummary } from '../module';

@Injectable({ providedIn: 'root' })
export class OnboardingService {
    private readonly baseUrl = `${environment.baseUrl}/onboarding`;
    constructor(private readonly http: HttpClient) {}
    context(): Observable<OnboardingContext> { return this.http.get<OnboardingContext>(`${this.baseUrl}/context`); }
    jobs(page = 0, size = 20): Observable<OnboardingPage<OnboardingJob>> { return this.http.get<OnboardingPage<OnboardingJob>>(`${this.baseUrl}/imports`, { params: { page, size, sort: 'insertDate,desc' } }); }
    job(id: number): Observable<OnboardingJob> { return this.http.get<OnboardingJob>(`${this.baseUrl}/imports/${id}`); }
    sections(id: number): Observable<OnboardingSectionSummary[]> { return this.http.get<OnboardingSectionSummary[]>(`${this.baseUrl}/imports/${id}/sections`); }
    rows(id: number, section?: OnboardingSection, status?: OnboardingRowStatus, page = 0, size = 50): Observable<OnboardingPage<OnboardingRow>> { let params = new HttpParams().set('page', page).set('size', size).set('sort', 'rowNumber,asc'); if (section) params = params.set('section', section); if (status) params = params.set('status', status); return this.http.get<OnboardingPage<OnboardingRow>>(`${this.baseUrl}/imports/${id}/rows`, { params }); }
    issues(id: number, severity?: 'ERROR' | 'WARNING', section?: OnboardingSection, page = 0, size = 50): Observable<OnboardingPage<OnboardingIssue>> { let params = new HttpParams().set('page', page).set('size', size).set('sort', 'rowNumber,asc'); if (severity) params = params.set('severity', severity); if (section) params = params.set('section', section); return this.http.get<OnboardingPage<OnboardingIssue>>(`${this.baseUrl}/imports/${id}/issues`, { params }); }
    upload(file: File, format: OnboardingFormat, csvSection: OnboardingSection | undefined, selectedSections: OnboardingSection[], key: string): Observable<OnboardingJob> { const data = new FormData(); data.append('file', file); let params = new HttpParams().set('format', format); if (csvSection) params = params.set('csvSection', csvSection); for (const section of selectedSections) params = params.append('selectedSections', section); return this.http.post<OnboardingJob>(`${this.baseUrl}/imports`, data, { params, headers: { 'Idempotency-Key': key } }); }
    apply(id: number, warningsAccepted: boolean, sendSetupEmails: boolean, key: string): Observable<OnboardingJob> { return this.http.post<OnboardingJob>(`${this.baseUrl}/imports/${id}/apply`, { warningsAccepted, sendSetupEmails }, { headers: { 'Idempotency-Key': key } }); }
    retryValidation(id: number): Observable<OnboardingJob> { return this.http.post<OnboardingJob>(`${this.baseUrl}/imports/${id}/retry-validation`, null); }
    retryCompensation(id: number): Observable<OnboardingJob> { return this.http.post<OnboardingJob>(`${this.baseUrl}/imports/${id}/retry-compensation`, null); }
    retryEmails(id: number): Observable<OnboardingJob> { return this.http.post<OnboardingJob>(`${this.baseUrl}/imports/${id}/retry-setup-emails`, null); }
    cancel(id: number): Observable<void> { return this.http.delete<void>(`${this.baseUrl}/imports/${id}`); }
    templateXlsx(): Observable<Blob> { return this.http.get(`${this.baseUrl}/templates/xlsx`, { responseType: 'blob' }); }
    templateCsv(section: OnboardingSection): Observable<Blob> { return this.http.get(`${this.baseUrl}/templates/csv`, { params: { section }, responseType: 'blob' }); }
    report(id: number, final = false): Observable<Blob> { return this.http.get(`${this.baseUrl}/imports/${id}/${final ? 'final-report' : 'validation-report'}`, { responseType: 'blob' }); }
}
