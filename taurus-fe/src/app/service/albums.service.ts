import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { RoleEnums } from '../constants';
import { Albums, AlbumsCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';
import { KeycloakService } from './keycloak.service';

@Injectable({
    providedIn: 'root'
})
export class AlbumsService extends CommonOpenSearchService<Albums, AlbumsCriteria> {

    constructor(protected override readonly http: HttpClient, private readonly keycloakService: KeycloakService) {
        super(http);
    }

    override resourceName(): string {
        if (this.keycloakService.currentUserRoles.every(role => role === RoleEnums.USER)) {
            return "user/albums";
        }

        return "albums";
    }
}
