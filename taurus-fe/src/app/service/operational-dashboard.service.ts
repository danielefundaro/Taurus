import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { OperationalDashboard } from '../module';

@Injectable({ providedIn: 'root' })
export class OperationalDashboardService {
    constructor(private readonly http: HttpClient) {}

    getOperations(): Observable<OperationalDashboard> {
        return this.http.get<OperationalDashboard>(`${environment.baseUrl}/dashboard/operations`);
    }
}
