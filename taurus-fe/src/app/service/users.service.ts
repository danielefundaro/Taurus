import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Users, TenantsCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

export interface UsersMeDTO {
    name?: string;
    lastName?: string;
    email?: string;
}

@Injectable({
    providedIn: 'root'
})
export class UsersService extends CommonOpenSearchService<Users, TenantsCriteria> {
    override resourceName(): string {
        return "users";
    }

    public partialUpdateOwn(dto: UsersMeDTO): Observable<Users> {
        return this.http.patch<Users>(`${this.baseUrl}/${this.resourceName()}/me`, dto);
    }
}
