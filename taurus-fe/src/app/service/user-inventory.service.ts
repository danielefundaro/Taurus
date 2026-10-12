import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { InventoryAssignment, InventoryAssignmentScope, InventoryAssignmentSummary, InventoryDecisionType, InventoryIssue, InventoryIssueSeverity, InventoryLoanSheetSummary, InventoryReturn, InventoryUserSummary, Page } from '../module';

@Injectable({ providedIn: 'root' })
export class UserInventoryService {
    private readonly baseUrl = `${environment.baseUrl}/user/inventory`;

    constructor(private readonly http: HttpClient) { }

    getAssignments(query = '', scope: InventoryAssignmentScope = 'POSSESSED', page = 0, size = 10, sort = 'assignedAt,desc', attention?: string): Observable<Page<InventoryAssignmentSummary>> {
        let params = new HttpParams().set('scope', scope).set('page', page).set('size', size).set('sort', sort);
        if (query.trim()) params = params.set('query', query.trim());
        if (attention) params = params.set('attention', attention);
        return this.http.get<Page<InventoryAssignmentSummary>>(`${this.baseUrl}/assignments`, { params });
    }

    getSummary(): Observable<InventoryUserSummary> {
        return this.http.get<InventoryUserSummary>(`${this.baseUrl}/summary`);
    }

    getAssignment(id: number): Observable<InventoryAssignment> {
        return this.http.get<InventoryAssignment>(`${this.baseUrl}/assignments/${id}`);
    }

    decide(id: number, decision: InventoryDecisionType, revisionHash: string, rejectionReason?: string): Observable<InventoryAssignment> {
        return this.http.post<InventoryAssignment>(`${this.baseUrl}/assignments/${id}/decision`, { decision, revisionHash, rejectionReason });
    }

    requestReturn(id: number, quantity: number, notes?: string): Observable<InventoryReturn> {
        return this.http.post<InventoryReturn>(`${this.baseUrl}/assignments/${id}/returns`, { quantity, notes });
    }

    uploadReturnPhoto(returnId: number, file: File): Observable<unknown> {
        const data = new FormData();
        data.append('file', file);
        return this.http.post(`${this.baseUrl}/returns/${returnId}/photos`, data);
    }

    photoUrl(id: number): string {
        return `${this.baseUrl}/photos/${id}`;
    }

    returnPhotoUrl(id: number): string {
        return `${this.baseUrl}/return-photos/${id}`;
    }

    createIssue(assignmentId: number, reportedQuantity: number, severity: InventoryIssueSeverity, description: string): Observable<InventoryIssue> {
        return this.http.post<InventoryIssue>(`${this.baseUrl}/assignments/${assignmentId}/issues`, { reportedQuantity, severity, description });
    }
    getIssue(id: number): Observable<InventoryIssue> {
        return this.http.get<InventoryIssue>(`${this.baseUrl}/issues/${id}`);
    }
    uploadIssuePhoto(issueId: number, file: File): Observable<unknown> {
        const data = new FormData();
        data.append('file', file);
        return this.http.post(`${this.baseUrl}/issues/${issueId}/photos`, data);
    }
    issuePhotoUrl(id: number): string {
        return `${this.baseUrl}/issue-photos/${id}`;
    }
    getLoanSheets(): Observable<InventoryLoanSheetSummary[]> {
        return this.http.get<InventoryLoanSheetSummary[]>(`${this.baseUrl}/loan-sheets`);
    }
    downloadLoanSheet(id: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/loan-sheets/${id}/pdf`, { responseType: 'blob' });
    }
}
