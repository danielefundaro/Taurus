import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
    AccountingYear,
    AccountingYearSummary,
    FinancialAccount,
    FinancialAccountStatement,
    FinancialAttachment,
    FinancialCategory,
    FinancialDashboard,
    FinancialDirection,
    FinancialEventSummary,
    FinancialMovement,
    FinancialTransferRequest,
    Page
} from '../module';

export type ReportFormat = 'csv' | 'xlsx' | 'pdf';

@Injectable({ providedIn: 'root' })
export class FinanceService {
    private readonly baseUrl = `${environment.baseUrl}/finance`;

    constructor(private readonly http: HttpClient) {}

    getDashboard(from?: string, to?: string): Observable<FinancialDashboard> {
        let params = new HttpParams();
        if (from) params = params.set('from', from);
        if (to) params = params.set('to', to);
        return this.http.get<FinancialDashboard>(`${this.baseUrl}/dashboard`, { params });
    }

    getAccounts(includeArchived = false): Observable<FinancialAccount[]> {
        return this.http.get<FinancialAccount[]>(`${this.baseUrl}/accounts`, { params: { includeArchived } });
    }

    createAccount(account: FinancialAccount): Observable<FinancialAccount> {
        return this.http.post<FinancialAccount>(`${this.baseUrl}/accounts`, account);
    }

    updateAccount(account: FinancialAccount): Observable<FinancialAccount> {
        return this.http.put<FinancialAccount>(`${this.baseUrl}/accounts/${account.id}`, account);
    }

    archiveAccount(id: number): Observable<void> {
        return this.http.patch<void>(`${this.baseUrl}/accounts/${id}/archive`, {});
    }

    getCategories(includeArchived = false): Observable<FinancialCategory[]> {
        return this.http.get<FinancialCategory[]>(`${this.baseUrl}/categories`, { params: { includeArchived } });
    }

    createCategory(category: FinancialCategory): Observable<FinancialCategory> {
        return this.http.post<FinancialCategory>(`${this.baseUrl}/categories`, category);
    }

    updateCategory(category: FinancialCategory): Observable<FinancialCategory> {
        return this.http.put<FinancialCategory>(`${this.baseUrl}/categories/${category.id}`, category);
    }

    archiveCategory(id: number): Observable<void> {
        return this.http.patch<void>(`${this.baseUrl}/categories/${id}/archive`, {});
    }

    getMovements(options: {
        page?: number;
        size?: number;
        sort?: string;
        from?: string;
        to?: string;
        accountId?: number;
        categoryId?: number;
        direction?: FinancialDirection;
        reconciled?: boolean;
        query?: string;
    }): Observable<Page<FinancialMovement>> {
        let params = new HttpParams()
            .set('page', options.page ?? 0)
            .set('size', options.size ?? 20)
            .set('sort', options.sort ?? 'bookingDate,desc');
        Object.entries(options).forEach(([key, value]) => {
            if (!['page', 'size', 'sort'].includes(key) && value !== undefined && value !== null && value !== '') {
                params = params.set(key, String(value));
            }
        });
        return this.http.get<Page<FinancialMovement>>(`${this.baseUrl}/movements`, { params });
    }

    createMovement(movement: FinancialMovement): Observable<FinancialMovement> {
        return this.http.post<FinancialMovement>(`${this.baseUrl}/movements`, movement);
    }

    getMovement(id: number): Observable<FinancialMovement> {
        return this.http.get<FinancialMovement>(`${this.baseUrl}/movements/${id}`);
    }

    updateMovement(movement: FinancialMovement): Observable<FinancialMovement> {
        return this.http.put<FinancialMovement>(`${this.baseUrl}/movements/${movement.id}`, movement);
    }

    deleteMovement(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/movements/${id}`);
    }

    reconcileMovement(id: number, reconciled: boolean, reference?: string): Observable<FinancialMovement> {
        return this.http.patch<FinancialMovement>(`${this.baseUrl}/movements/${id}/reconciliation`, { reconciled, reference });
    }

    createTransfer(transfer: FinancialTransferRequest): Observable<unknown> {
        return this.http.post(`${this.baseUrl}/transfers`, transfer);
    }

    getAttachments(movementId: number): Observable<FinancialAttachment[]> {
        return this.http.get<FinancialAttachment[]>(`${this.baseUrl}/movements/${movementId}/attachments`);
    }

    uploadAttachment(movementId: number, file: File, description?: string): Observable<FinancialAttachment> {
        const data = new FormData();
        data.append('file', file);
        if (description) data.append('description', description);
        return this.http.post<FinancialAttachment>(`${this.baseUrl}/movements/${movementId}/attachments`, data);
    }

    attachmentUrl(id: number): string {
        return `${this.baseUrl}/attachments/${id}`;
    }

    downloadAttachment(id: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/attachments/${id}`, { responseType: 'blob' });
    }

    deleteAttachment(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/attachments/${id}`);
    }

    getEvents(page = 0, size = 20, sort = 'startDate,desc'): Observable<Page<FinancialEventSummary>> {
        return this.http.get<Page<FinancialEventSummary>>(`${this.baseUrl}/events`, { params: { page, size, sort } });
    }

    getEvent(id: number): Observable<FinancialEventSummary> {
        return this.http.get<FinancialEventSummary>(`${this.baseUrl}/events/${id}`);
    }

    updateEventBudget(eventId: number, fee: number, costs: Array<{ description: string; amount: number }>): Observable<FinancialEventSummary> {
        return this.http.patch<FinancialEventSummary>(`${this.baseUrl}/events/${eventId}/budget`, { fee, costs });
    }

    getYears(): Observable<AccountingYear[]> {
        return this.http.get<AccountingYear[]>(`${this.baseUrl}/years`);
    }

    exportCashbook(format: ReportFormat, from?: string, to?: string, accountId?: number, categoryId?: number): Observable<Blob> {
        let params = new HttpParams().set('format', format);
        if (from) params = params.set('from', from);
        if (to) params = params.set('to', to);
        if (accountId) params = params.set('accountId', accountId);
        if (categoryId) params = params.set('categoryId', categoryId);
        return this.http.get(`${this.baseUrl}/reports/cashbook`, { params, responseType: 'blob' });
    }

    getAccountStatement(accountId: number, from?: string, to?: string): Observable<FinancialAccountStatement> {
        let params = new HttpParams();
        if (from) params = params.set('from', from);
        if (to) params = params.set('to', to);
        return this.http.get<FinancialAccountStatement>(`${this.baseUrl}/accounts/${accountId}/statement`, { params });
    }

    getYear(year: number): Observable<AccountingYear> {
        return this.http.get<AccountingYear>(`${this.baseUrl}/years/${year}`);
    }

    getYearSummary(year: number): Observable<AccountingYearSummary> {
        return this.http.get<AccountingYearSummary>(`${this.baseUrl}/years/${year}/summary`);
    }

    exportAccountStatement(format: ReportFormat, accountId: number, from?: string, to?: string): Observable<Blob> {
        let params = new HttpParams().set('format', format).set('accountId', accountId);
        if (from) params = params.set('from', from);
        if (to) params = params.set('to', to);
        return this.http.get(`${this.baseUrl}/reports/account-statement`, { params, responseType: 'blob' });
    }

    exportEventsReport(format: ReportFormat, from?: string, to?: string): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/reports/events`, { params: this.periodParams(format, from, to), responseType: 'blob' });
    }

    exportCategoriesReport(format: ReportFormat, from?: string, to?: string): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/reports/categories`, { params: this.periodParams(format, from, to), responseType: 'blob' });
    }

    exportAnnualReport(format: ReportFormat, year: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/reports/annual`, {
            params: new HttpParams().set('format', format).set('year', year),
            responseType: 'blob'
        });
    }

    private periodParams(format: ReportFormat, from?: string, to?: string): HttpParams {
        let params = new HttpParams().set('format', format);
        if (from) params = params.set('from', from);
        if (to) params = params.set('to', to);
        return params;
    }

    rollover(year: number): Observable<AccountingYear> {
        return this.http.post<AccountingYear>(`${this.baseUrl}/years/${year}/rollover`, null);
    }

    recalculate(year: number): Observable<AccountingYear> {
        return this.http.post<AccountingYear>(`${this.baseUrl}/years/${year}/recalculate`, null);
    }
}

/** Converte una data del calendario nel formato ISO accettato dalle API economiche. */
export function toIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

/** Converte una data ISO restituita dalle API in una data del calendario. */
export function parseIsoDate(value?: string): Date | undefined {
    if (!value) return undefined;
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
}
