import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
    InventoryAdminSummary,
    InventoryAssignment,
    InventoryAssignmentRequest,
    InventoryErasureRequest,
    InventoryIssue,
    InventoryIssueSeverity,
    InventoryIssueStatus,
    InventoryItem,
    InventoryLabelRequest,
    InventoryLoanExpirationRange,
    InventoryLoanSheetCloseRequest,
    InventoryLoanSheetDetail,
    InventoryLoanSheetIssueRequest,
    InventoryLoanSheetProposal,
    InventoryLoanSheetSummary,
    InventoryLoanDurations,
    InventoryLoanTemplateCheck,
    InventoryLoanTemplateFile,
    InventoryLoanTemplateOverview,
    InventoryPhoto,
    InventoryRenewalRequest,
    InventoryReturn,
    InventorySubstitutionRequest,
    Page,
    SignatureAct,
    SignatureAssociationSignerOption,
    SignatureDocumentRole,
    SignatureEvidence
} from '../module';

@Injectable({ providedIn: 'root' })
export class InventoryService {
    private readonly baseUrl = `${environment.baseUrl}/inventory`;

    constructor(private readonly http: HttpClient) { }

    getItems(query = '', page = 0, size = 100, sort = 'name,asc', attention?: string): Observable<Page<InventoryItem>> {
        let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
        if (query.trim()) params = params.set('query', query.trim());
        if (attention) params = params.set('attention', attention);
        return this.http.get<Page<InventoryItem>>(`${this.baseUrl}/items`, { params });
    }

    getSummary(): Observable<InventoryAdminSummary> {
        return this.http.get<InventoryAdminSummary>(`${this.baseUrl}/summary`);
    }

    getItem(id: number): Observable<InventoryItem> {
        return this.http.get<InventoryItem>(`${this.baseUrl}/items/${id}`);
    }
    createItem(item: InventoryItem): Observable<InventoryItem> {
        return this.http.post<InventoryItem>(`${this.baseUrl}/items`, item);
    }
    updateItem(id: number, item: InventoryItem): Observable<InventoryItem> {
        return this.http.put<InventoryItem>(`${this.baseUrl}/items/${id}`, item);
    }
    deleteItem(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/items/${id}`);
    }
    assign(itemId: number, request: InventoryAssignmentRequest, context?: HttpContext): Observable<InventoryAssignment> {
        return this.http.post<InventoryAssignment>(`${this.baseUrl}/items/${itemId}/assignments`, request, { context });
    }
    updateAssignment(id: number, request: InventoryAssignmentRequest, context?: HttpContext): Observable<InventoryAssignment> {
        return this.http.put<InventoryAssignment>(`${this.baseUrl}/assignments/${id}`, request, { context });
    }
    deleteAssignment(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/assignments/${id}`);
    }
    reissue(id: number): Observable<InventoryAssignment> {
        return this.http.post<InventoryAssignment>(`${this.baseUrl}/assignments/${id}/reissue`, null);
    }
    uploadPhoto(itemId: number, file: File): Observable<unknown> {
        const data = new FormData();
        data.append('file', file);
        return this.http.post(`${this.baseUrl}/items/${itemId}/photos`, data);
    }
    deletePhoto(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/photos/${id}`);
    }
    reorderPhotos(itemId: number, photoIds: number[]): Observable<InventoryPhoto[]> {
        return this.http.put<InventoryPhoto[]>(`${this.baseUrl}/items/${itemId}/photos/order`, { photoIds });
    }
    setPreviewPhoto(itemId: number, photoId: number): Observable<InventoryPhoto[]> {
        return this.http.put<InventoryPhoto[]>(`${this.baseUrl}/items/${itemId}/photos/${photoId}/preview`, null);
    }
    photoUrl(id: number): string {
        return `${this.baseUrl}/photos/${id}`;
    }
    getUserAssignments(userIndex: number): Observable<InventoryAssignment[]> {
        return this.http.get<InventoryAssignment[]>(`${this.baseUrl}/users/${userIndex}/assignments`);
    }
    requestReturn(id: number, quantity: number, notes?: string): Observable<InventoryReturn> {
        return this.http.post<InventoryReturn>(`${this.baseUrl}/assignments/${id}/returns`, { quantity, notes });
    }
    completeReturn(returnId: number, quantity: number, condition?: string, notes?: string): Observable<InventoryReturn> {
        return this.http.post<InventoryReturn>(`${this.baseUrl}/returns/${returnId}/complete`, { quantity, condition, notes });
    }
    deleteReturn(returnId: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/returns/${returnId}`);
    }
    uploadReturnPhoto(returnId: number, file: File): Observable<unknown> {
        const data = new FormData();
        data.append('file', file);
        return this.http.post(`${this.baseUrl}/returns/${returnId}/photos`, data);
    }
    getErasureRequests(): Observable<InventoryErasureRequest[]> {
        return this.http.get<InventoryErasureRequest[]>(`${this.baseUrl}/erasure-requests`);
    }
    completeErasureRequest(id: number): Observable<InventoryErasureRequest> {
        return this.http.post<InventoryErasureRequest>(`${this.baseUrl}/erasure-requests/${id}/complete`, null);
    }
    qrCodeUrl(itemId: number, size: 256 | 512 = 256): string {
        return `${this.baseUrl}/items/${itemId}/qr-code.png?size=${size}`;
    }
    generateLabels(request: InventoryLabelRequest): Observable<Blob> {
        return this.http.post(`${this.baseUrl}/labels`, request, { responseType: 'blob' });
    }
    rotateQrCode(itemId: number, reason: string): Observable<{ version: number; issuedAt: string }> {
        return this.http.post<{ version: number; issuedAt: string }>(`${this.baseUrl}/items/${itemId}/qr-code/rotate`, { reason });
    }
    getIssues(itemId: number): Observable<InventoryIssue[]> {
        return this.http.get<InventoryIssue[]>(`${this.baseUrl}/items/${itemId}/issues`);
    }
    createIssue(itemId: number, reportedQuantity: number, severity: InventoryIssueSeverity, description: string): Observable<InventoryIssue> {
        return this.http.post<InventoryIssue>(`${this.baseUrl}/items/${itemId}/issues`, { reportedQuantity, severity, description });
    }
    transitionIssue(id: number, status: InventoryIssueStatus, version: number, resolutionNotes?: string, itemConditionStatus?: string): Observable<InventoryIssue> {
        return this.http.patch<InventoryIssue>(`${this.baseUrl}/issues/${id}/status`, { status, version, resolutionNotes, itemConditionStatus });
    }
    uploadIssuePhoto(issueId: number, file: File): Observable<unknown> {
        const data = new FormData();
        data.append('file', file);
        return this.http.post(`${this.baseUrl}/issues/${issueId}/photos`, data);
    }
    issuePhotoUrl(id: number): string {
        return `${this.baseUrl}/issue-photos/${id}`;
    }

    getLoanSheets(userIndex: number): Observable<InventoryLoanSheetSummary[]> {
        return this.http.get<InventoryLoanSheetSummary[]>(`${this.baseUrl}/users/${userIndex}/loan-sheets`);
    }
    /** Scadenze ammesse per un comodato a scadenza del socio consegnato in `deliveredOn` (oggi se assente). */
    getLoanExpirationRange(userIndex: number, deliveredOn?: string): Observable<InventoryLoanExpirationRange> {
        const params = deliveredOn ? new HttpParams().set('deliveredOn', deliveredOn) : undefined;
        return this.http.get<InventoryLoanExpirationRange>(`${this.baseUrl}/users/${userIndex}/loan-expiration-range`, { params });
    }
    getLoanSheetProposal(userIndex: number): Observable<InventoryLoanSheetProposal> {
        return this.http.get<InventoryLoanSheetProposal>(`${this.baseUrl}/users/${userIndex}/loan-sheets/proposal`);
    }
    previewLoanSheet(userIndex: number, request: InventoryLoanSheetIssueRequest): Observable<Blob> {
        return this.http.post(`${this.baseUrl}/users/${userIndex}/loan-sheets/preview`, request, { responseType: 'blob' });
    }
    issueLoanSheet(userIndex: number, request: InventoryLoanSheetIssueRequest, context?: HttpContext): Observable<InventoryLoanSheetDetail> {
        return this.http.post<InventoryLoanSheetDetail>(`${this.baseUrl}/users/${userIndex}/loan-sheets`, request, { context });
    }
    getLoanSheet(id: number): Observable<InventoryLoanSheetDetail> {
        return this.http.get<InventoryLoanSheetDetail>(`${this.baseUrl}/loan-sheets/${id}`);
    }
    downloadLoanSheet(id: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/loan-sheets/${id}/pdf`, { responseType: 'blob' });
    }
    linkLoanSheetAssignment(sheetId: number, assignmentId: number): Observable<InventoryLoanSheetDetail> {
        return this.http.post<InventoryLoanSheetDetail>(`${this.baseUrl}/loan-sheets/${sheetId}/assignments/${assignmentId}`, null);
    }
    closeLoanSheet(id: number, request: InventoryLoanSheetCloseRequest): Observable<InventoryLoanSheetDetail> {
        return this.http.post<InventoryLoanSheetDetail>(`${this.baseUrl}/loan-sheets/${id}/close`, request);
    }
    settleLoanSheet(id: number): Observable<InventoryLoanSheetDetail> {
        return this.http.post<InventoryLoanSheetDetail>(`${this.baseUrl}/loan-sheets/${id}/settle`, null);
    }
    getSignatureActs(sheetId: number): Observable<SignatureAct[]> {
        return this.http.get<SignatureAct[]>(`${this.baseUrl}/loan-sheets/${sheetId}/signature-acts`);
    }
    downloadSignatureDossier(sheetId: number, context?: HttpContext): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/loan-sheets/${sheetId}/signature-dossier`, { responseType: 'blob', context });
    }
    getSignatureEvidence(actId: number): Observable<SignatureEvidence[]> {
        return this.http.get<SignatureEvidence[]>(`${this.baseUrl}/signature-acts/${actId}/evidence`);
    }
    downloadSignatureDocument(actId: number, role: SignatureDocumentRole): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/signature-acts/${actId}/documents/${role}`, { responseType: 'blob' });
    }
    downloadSignatureAttestation(actId: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/signature-acts/${actId}/attestation`, { responseType: 'blob' });
    }
    getAssociationSigners(userIndex: number): Observable<SignatureAssociationSignerOption[]> {
        return this.http.get<SignatureAssociationSignerOption[]>(`${this.baseUrl}/users/${userIndex}/association-signers`);
    }
    changeAssociationSigner(actId: number, userId: string, entityVersion: number, context?: HttpContext): Observable<SignatureAct> {
        return this.http.put<SignatureAct>(`${this.baseUrl}/signature-acts/${actId}/association-signer`, { userId, entityVersion }, { context });
    }
    /** Invia un nuovo link al genitore o tutore e revoca il precedente. */
    resendSignatureLink(actId: number, signerId: number, context?: HttpContext): Observable<SignatureAct> {
        return this.http.post<SignatureAct>(`${this.baseUrl}/signature-acts/${actId}/links/${signerId}/resend`, null, { context });
    }
    revokeSignatureLink(actId: number, signerId: number, context?: HttpContext): Observable<SignatureAct> {
        return this.http.delete<SignatureAct>(`${this.baseUrl}/signature-acts/${actId}/links/${signerId}`, { context });
    }
    cancelLoanSheet(id: number, reason: string): Observable<InventoryLoanSheetDetail> {
        return this.http.post<InventoryLoanSheetDetail>(`${this.baseUrl}/loan-sheets/${id}/cancel`, { reason });
    }
    renewAssignment(id: number, request: InventoryRenewalRequest): Observable<InventoryAssignment> {
        return this.http.post<InventoryAssignment>(`${this.baseUrl}/assignments/${id}/renewals`, request);
    }
    substituteAssignment(id: number, request: InventorySubstitutionRequest): Observable<InventoryAssignment> {
        return this.http.post<InventoryAssignment>(`${this.baseUrl}/assignments/${id}/substitution`, request);
    }

    getLoanTemplate(): Observable<InventoryLoanTemplateOverview> {
        return this.http.get<InventoryLoanTemplateOverview>(`${this.baseUrl}/loan-template`);
    }
    updateLoanDurations(durations: InventoryLoanDurations, entityVersion: number): Observable<InventoryLoanTemplateOverview> {
        return this.http.put<InventoryLoanTemplateOverview>(`${this.baseUrl}/loan-template/durations`, { durations, entityVersion });
    }

    updateLoanTemplateSignature(enabled: boolean, entityVersion: number, context?: HttpContext): Observable<InventoryLoanTemplateOverview> {
        return this.http.put<InventoryLoanTemplateOverview>(`${this.baseUrl}/loan-template/signature`, { enabled, entityVersion }, { context });
    }
    checkLoanTemplate(file: InventoryLoanTemplateFile): Observable<InventoryLoanTemplateCheck> {
        return this.http.post<InventoryLoanTemplateCheck>(`${this.baseUrl}/loan-template/check`, file);
    }
    previewLoanTemplate(file: InventoryLoanTemplateFile, context?: HttpContext): Observable<Blob> {
        return this.http.post(`${this.baseUrl}/loan-template/preview`, file, { responseType: 'blob', context });
    }
    activateLoanTemplate(file: InventoryLoanTemplateFile, context?: HttpContext): Observable<InventoryLoanTemplateOverview> {
        return this.http.post<InventoryLoanTemplateOverview>(`${this.baseUrl}/loan-template`, file, { context });
    }
    activateLoanTemplateVersion(id: number, context?: HttpContext): Observable<InventoryLoanTemplateOverview> {
        return this.http.post<InventoryLoanTemplateOverview>(`${this.baseUrl}/loan-template/versions/${id}/activate`, null, { context });
    }
    useDefaultLoanTemplate(): Observable<InventoryLoanTemplateOverview> {
        return this.http.post<InventoryLoanTemplateOverview>(`${this.baseUrl}/loan-template/use-default`, null);
    }
    downloadDefaultLoanTemplate(): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/loan-template/default/file`, { responseType: 'blob' });
    }
    previewDefaultLoanTemplate(context?: HttpContext): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/loan-template/default/preview`, { responseType: 'blob', context });
    }
    downloadLoanTemplateVersion(id: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/loan-template/versions/${id}/file`, { responseType: 'blob' });
    }
    previewLoanTemplateVersion(id: number, context?: HttpContext): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/loan-template/versions/${id}/preview`, { responseType: 'blob', context });
    }
}
