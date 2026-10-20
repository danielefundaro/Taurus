import { HTTP_INTERCEPTORS, HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';
import { KeycloakService, LoadingService, ToastService, withHandledErrors } from '../service';
import { HttpInterceptorService } from './http-interceptor.service';

describe('HttpInterceptorService on the public signature page', () => {
    let http: HttpClient;
    let controller: HttpTestingController;
    let keycloak: jasmine.SpyObj<KeycloakService>;
    let toast: jasmine.SpyObj<ToastService>;
    const link = `${environment.baseUrl}/signature-links/v1/${'A'.repeat(43)}`;

    beforeEach(() => {
        keycloak = jasmine.createSpyObj<KeycloakService>('KeycloakService', ['logout'], { isUserLoggedIn: false, token: undefined });
        toast = jasmine.createSpyObj<ToastService>('ToastService', ['error']);
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting(),
                { provide: HTTP_INTERCEPTORS, useClass: HttpInterceptorService, multi: true },
                { provide: KeycloakService, useValue: keycloak },
                { provide: ToastService, useValue: toast },
                { provide: LoadingService, useValue: { loading: false } },
                { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) }
            ]
        });
        http = TestBed.inject(HttpClient);
        controller = TestBed.inject(HttpTestingController);
    });
    afterEach(() => controller.verify());

    it('calls the endpoints of the link without a token and without logging in', () => {
        http.get(link).subscribe();

        const request = controller.expectOne(link);
        expect(request.request.headers.has('Authorization')).toBeFalse();
        request.flush({});
        expect(keycloak.logout).not.toHaveBeenCalled();
    });

    it('shows the errors of the page unless the page handles them', () => {
        http.get(link).subscribe({ error: () => undefined });
        controller.expectOne(link).flush({ message: 'error.signature.challenge.limit', args: { maxSends: 5 } }, { status: 409, statusText: 'Conflict' });
        expect(toast.error).toHaveBeenCalledTimes(1);

        http.get(link, { context: withHandledErrors(['http.404']) }).subscribe({ error: () => undefined });
        controller.expectOne(link).flush(null, { status: 404, statusText: 'Not Found' });
        expect(toast.error).toHaveBeenCalledTimes(1);
        expect(keycloak.logout).not.toHaveBeenCalled();
    });
});
