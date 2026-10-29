import { HTTP_INTERCEPTORS, HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';
import { KeycloakService, LoadingService, TenantFeatureService, ToastService, withHandledErrors } from '../service';
import { of } from 'rxjs';
import { TenantFeature } from '../module';
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

describe('HttpInterceptorService when a tenant feature is disabled', () => {
    let http: HttpClient;
    let controller: HttpTestingController;
    let router: jasmine.SpyObj<Router>;
    const url = `${environment.baseUrl}/inventory/items`;

    const configure = (openFeature?: TenantFeature): void => {
        const root = { data: {}, children: [{ data: openFeature ? { feature: openFeature } : {}, children: [] }] };
        router = jasmine.createSpyObj<Router>('Router', ['navigate'], { routerState: { snapshot: { root } } as never });
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting(),
                { provide: HTTP_INTERCEPTORS, useClass: HttpInterceptorService, multi: true },
                { provide: KeycloakService, useValue: jasmine.createSpyObj<KeycloakService>('KeycloakService', ['logout'], { isUserLoggedIn: true, token: 'token' }) },
                { provide: ToastService, useValue: jasmine.createSpyObj<ToastService>('ToastService', ['error']) },
                { provide: LoadingService, useValue: { loading: false } },
                { provide: TenantFeatureService, useValue: { refresh: () => of({}) } },
                { provide: Router, useValue: router }
            ]
        });
        http = TestBed.inject(HttpClient);
        controller = TestBed.inject(HttpTestingController);
    };

    afterEach(() => {
        controller.verify();
        TestBed.resetTestingModule();
    });

    const failWith = (key: string): void => {
        http.get(url).subscribe({ error: () => undefined });
        controller.expectOne(url).flush({ message: `error.${key}` }, { status: 403, statusText: 'Forbidden' });
    };

    it('leaves the page that is dedicated to the feature', () => {
        configure(TenantFeature.INVENTORY);

        failWith('tenantFeature.inventory.disabled');

        expect(router.navigate).toHaveBeenCalledWith(['/']);
    });

    it('stays on a shared page and lets it hide the section', () => {
        configure(TenantFeature.INVENTORY);

        failWith('tenantFeature.inventoryQr.disabled');

        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('stays on a page that declares no feature at all', () => {
        configure();

        failWith('tenantFeature.eventPreparation.disabled');

        expect(router.navigate).not.toHaveBeenCalled();
    });
});

describe('HttpInterceptorService on a conditional read', () => {
    let http: HttpClient;
    let controller: HttpTestingController;
    let toast: jasmine.SpyObj<ToastService>;
    const url = `${environment.baseUrl}/onboarding/imports/42`;

    beforeEach(() => {
        toast = jasmine.createSpyObj<ToastService>('ToastService', ['error']);
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting(),
                { provide: HTTP_INTERCEPTORS, useClass: HttpInterceptorService, multi: true },
                { provide: KeycloakService, useValue: jasmine.createSpyObj<KeycloakService>('KeycloakService', ['logout'], { isUserLoggedIn: true, token: 'token' }) },
                { provide: ToastService, useValue: toast },
                { provide: LoadingService, useValue: { loading: false } },
                { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) }
            ]
        });
        http = TestBed.inject(HttpClient);
        controller = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        controller.verify();
        TestBed.resetTestingModule();
    });

    it('turns «not modified» into the empty answer the caller asked for', () => {
        let status: number | undefined;
        let failed = false;
        http.get(url, { observe: 'response' }).subscribe({ next: (response) => (status = response.status), error: () => (failed = true) });

        controller.expectOne(url).flush(null, { status: 304, statusText: 'Not Modified' });

        expect(status).toBe(304);
        expect(failed).toBeFalse();
        expect(toast.error).not.toHaveBeenCalled();
    });
});
