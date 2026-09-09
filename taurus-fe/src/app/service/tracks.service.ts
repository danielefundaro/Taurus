import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RoleEnums } from '../constants';
import { ImageTransformRecipe, TrackPageAnalysis, TrackPageEditResult, TrackUploadJob, Tracks, TracksCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';
import { KeycloakService } from './keycloak.service';

@Injectable({
    providedIn: 'root'
})
export class TracksService extends CommonOpenSearchService<Tracks, TracksCriteria> {
    constructor(
        protected override readonly http: HttpClient,
        private readonly keycloakService: KeycloakService
    ) {
        super(http);
    }

    override resourceName(): string {
        if (this.keycloakService.currentUserRole === RoleEnums.USER_EXTERNAL) {
            return 'external/tracks';
        }
        if (this.keycloakService.currentUserRole === RoleEnums.USER) {
            return 'user/tracks';
        }
        return 'tracks';
    }

    public stream(id?: number): string {
        if (id) {
            return `${this.baseUrl}/${this.resourceName()}/${id}/stream`;
        }

        return `${this.baseUrl}/${this.resourceName()}/stream`;
    }

    public uploadPdf(formData: FormData, id?: number): Observable<TrackUploadJob> {
        return this.http.post<TrackUploadJob>(this.stream(id), formData);
    }

    public getUploadJobs(id: number): Observable<TrackUploadJob[]> {
        return this.http.get<TrackUploadJob[]>(`${this.baseUrl}/${this.resourceName()}/${id}/upload-jobs`);
    }

    public retryUploadJob(trackId: number, jobId: number): Observable<TrackUploadJob> {
        return this.http.post<TrackUploadJob>(`${this.baseUrl}/${this.resourceName()}/${trackId}/upload-jobs/${jobId}/retry`, {});
    }

    public analyzePage(trackId: number, scoreId: number, mediaId: number): Observable<TrackPageAnalysis> {
        return this.http.get<TrackPageAnalysis>(`${this.baseUrl}/tracks/${trackId}/scores/${scoreId}/media/${mediaId}/analysis`);
    }

    public editPage(trackId: number, scoreId: number, mediaId: number, recipe: ImageTransformRecipe, idempotencyKey: string): Observable<TrackPageEditResult> {
        return this.http.post<TrackPageEditResult>(`${this.baseUrl}/tracks/${trackId}/scores/${scoreId}/media/${mediaId}/edits`, recipe, {
            headers: { 'Idempotency-Key': idempotencyKey }
        });
    }
}
