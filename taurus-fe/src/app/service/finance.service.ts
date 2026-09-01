import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AccountingYear, FinancialAccount, FinancialAttachment, FinancialCategory, FinancialDashboard, FinancialDirection, FinancialEventSummary, FinancialMovement, FinancialTransferRequest, Page } from '../module';

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

    exportCashbook(format: 'csv' | 'xlsx' | 'pdf', from?: string, to?: string, accountId?: number, categoryId?: number): Observable<Blob> {
        let params = new HttpParams().set('format', format);
        if (from) params = params.set('from', from);
        if (to) params = params.set('to', to);
        if (accountId) params = params.set('accountId', accountId);
        if (categoryId) params = params.set('categoryId', categoryId);
        return this.http.get(`${this.baseUrl}/reports/cashbook`, { params, responseType: 'blob' });
    }

    rollover(year: number): Observable<AccountingYear> {
        return this.http.post<AccountingYear>(`${this.baseUrl}/years/${year}/rollover`, null);
    }

    recalculate(year: number): Observable<AccountingYear> {
        return this.http.post<AccountingYear>(`${this.baseUrl}/years/${year}/recalculate`, null);
    }
}
