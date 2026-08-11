import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Tenants, TenantsCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

@Injectable({
    providedIn: 'root'
})
export class TenantsService extends CommonOpenSearchService<Tenants, TenantsCriteria> {
    override resourceName(): string {
        return "tenants";
    }

    public deleteForGdpr(id: string): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${this.resourceName()}/${id}/gdpr`);
    }
}
