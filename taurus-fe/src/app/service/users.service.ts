import { Injectable } from '@angular/core';
import { Users, TenantsCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

@Injectable({
    providedIn: 'root'
})
export class UsersService extends CommonOpenSearchService<Users, TenantsCriteria> {
    override resourceName(): string {
        return "users";
    }
}
