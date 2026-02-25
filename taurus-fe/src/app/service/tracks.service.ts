import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { RoleEnums } from '../constants';
import { Tracks, TracksCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';
import { KeycloakService } from './keycloak.service';

@Injectable({
    providedIn: 'root'
})
export class TracksService extends CommonOpenSearchService<Tracks, TracksCriteria> {

    constructor(protected override readonly http: HttpClient, private readonly keycloakService: KeycloakService) {
        super(http);
    }

    override resourceName(): string {
        if (this.keycloakService.currentUserRoles.every(role => role === RoleEnums.USER)) {
            return "user/tracks";
        }

        return "tracks";
    }

    public stream(id?: string): string {
        if (id) {
            return `${this.baseUrl}/${this.resourceName()}/${id}/stream`;
        }

        return `${this.baseUrl}/${this.resourceName()}/stream`;
    }
}
