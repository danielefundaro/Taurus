import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { RoleEnums } from '../constants';
import { ImageTransformRecipe } from '../module';
import { KeycloakService } from './keycloak.service';
import { TracksService } from './tracks.service';

describe('TracksService image editing', () => {
    let service: TracksService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: KeycloakService, useValue: { currentUserRole: RoleEnums.ARCHIVIST } }]
        });
        service = TestBed.inject(TracksService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('uses the contextual track page endpoint for analysis', () => {
        service.analyzePage(1, 2, 3).subscribe();

        const request = http.expectOne(`${environment.baseUrl}/tracks/1/scores/2/media/3/analysis`);
        expect(request.request.method).toBe('GET');
        request.flush({});
    });

    it('sends the recipe and idempotency key when saving', () => {
        const recipe: ImageTransformRecipe = {
            expectedTrackVersion: 4,
            recipeVersion: 1,
            rotationQuarterTurns: 1,
            deskewDegrees: 0,
            crops: [],
            grayscale: false,
            brightness: 0,
            contrast: 0,
            autoContrast: false,
            threshold: null
        };

        service.editPage(1, 2, 3, recipe, 'request-id').subscribe();

        const request = http.expectOne(`${environment.baseUrl}/tracks/1/scores/2/media/3/edits`);
        expect(request.request.method).toBe('POST');
        expect(request.request.headers.get('Idempotency-Key')).toBe('request-id');
        expect(request.request.body).toEqual(recipe);
        request.flush({});
    });
});
