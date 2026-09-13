import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { InventoryScanResult } from '../module';

@Injectable({ providedIn: 'root' })
export class InventoryScanService {
    constructor(private readonly http: HttpClient) { }

    resolve(publicId: string): Observable<InventoryScanResult> {
        return this.http.get<InventoryScanResult>(`${environment.baseUrl}/inventory-scan/v1/${encodeURIComponent(publicId)}`);
    }
}
