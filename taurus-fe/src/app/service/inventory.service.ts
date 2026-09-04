import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { InventoryAdminSummary, InventoryAssignment, InventoryAssignmentRequest, InventoryErasureRequest, InventoryItem, InventoryPhoto, InventoryReturn, Page } from '../module';

@Injectable({ providedIn: 'root' })
export class InventoryService {
    private readonly baseUrl = `${environment.baseUrl}/inventory`;

    constructor(private readonly http: HttpClient) {}

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
    assign(itemId: number, request: InventoryAssignmentRequest): Observable<InventoryAssignment> {
        return this.http.post<InventoryAssignment>(`${this.baseUrl}/items/${itemId}/assignments`, request);
    }
    updateAssignment(id: number, request: InventoryAssignmentRequest): Observable<InventoryAssignment> {
        return this.http.put<InventoryAssignment>(`${this.baseUrl}/assignments/${id}`, request);
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
    downloadReport(userIndex: number, includeAssigned = true, includeReturned = true, includePhotos = true): Observable<Blob> {
        const url = `${this.baseUrl}/users/${userIndex}/report`;
        const params = new HttpParams().set('includeAssigned', includeAssigned).set('includeReturned', includeReturned).set('includePhotos', includePhotos);
        return this.http.get(url, { params, responseType: 'blob' });
    }
    getErasureRequests(): Observable<InventoryErasureRequest[]> {
        return this.http.get<InventoryErasureRequest[]>(`${this.baseUrl}/erasure-requests`);
    }
    completeErasureRequest(id: number): Observable<InventoryErasureRequest> {
        return this.http.post<InventoryErasureRequest>(`${this.baseUrl}/erasure-requests/${id}/complete`, null);
    }
}
