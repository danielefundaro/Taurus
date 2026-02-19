import { Injectable } from '@angular/core';
import { Tenants, TenantsCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

@Injectable({
    providedIn: 'root'
})
export class TenantsService extends CommonOpenSearchService<Tenants, TenantsCriteria> {
    override resourceName(): string {
        return "tenants";
    }
}
