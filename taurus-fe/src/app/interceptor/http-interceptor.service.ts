import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';
import { BehaviorSubject, finalize, Observable, switchMap, throwError } from 'rxjs';
import { catchError, filter, take } from 'rxjs/operators';
import { ApiErrorService, KeycloakService, LoadingService, PUBLIC_SIGNATURE_API, ResolvedApiError, TenantFeatureService, TenantStatusService, ToastService } from '../service';
import { Router } from '@angular/router';

@Injectable({
    providedIn: 'root'
})
export class HttpInterceptorService implements HttpInterceptor {
    private readonly _nonProtectedRoutes: (RegExp | string)[] = [];
    private readonly excludedRoutes = ['api/preferences', 'api/notices', '/count'];

    private readonly httpRequestStack = new Array<HttpRequest<any>>();
    private isRefreshing = false;
    private endingSession = false;
    private readonly refreshTokenSubject: BehaviorSubject<any>;

    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly toastService: ToastService,
        private readonly loadingService: LoadingService,
        private readonly apiErrorService: ApiErrorService,
        private readonly router: Router,
        private readonly injector: Injector
    ) {
        this.refreshTokenSubject = new BehaviorSubject<any>(null);
    }

    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        if (this._isFreeRoute(req.url)) {
            return next.handle(req);
        }

        if (req.url.includes(PUBLIC_SIGNATURE_API)) {
            // Public signature page of a parent: no token and no login, errors are shown as elsewhere.
            return next.handle(req).pipe(
                catchError((error: HttpErrorResponse) =>
                    this.apiErrorService.readBody(error).pipe(
                        switchMap((readable) => {
                            this.notify(this.apiErrorService.resolve(readable, req));
                            return throwError(() => readable);
                        })
                    )
                )
            );
        }

        if (!this.keycloakService.isUserLoggedIn) {
            this.keycloakService.logout();
            return next.handle(req).pipe(
                catchError((error: HttpErrorResponse) =>
                    this.apiErrorService.readBody(error).pipe(
                        switchMap((readable) => {
                            this.notify(this.apiErrorService.resolve(readable, req));
                            return throwError(() => readable);
                        })
                    )
                )
            );
        }

        if (!this.excludedRoutes.some((route) => req.url.includes(route))) {
            this.loadingService.loading = true;
            this.httpRequestStack.push(req);
        }

        const token = this.getAuthToken();
        const authReq = this.addTokenHeader(req, token);

        return next.handle(authReq).pipe(
            finalize(() => {
                if (!this.excludedRoutes.some((route) => authReq.url.includes(route))) {
                    this.httpRequestStack.pop();
                }

                if (this.httpRequestStack.length === 0) {
                    this.loadingService.loading = false;
                }
            }),
            catchError((error: HttpErrorResponse) => this.apiErrorService.readBody(error).pipe(switchMap((readable) => this.handleError(readable, authReq, next, true))))
        );
    }

    /**
     * Applies the behavior registered for the error key, or shows the toast built by ApiErrorService.
     * The error is always propagated to the caller, which may show it next to a field (HANDLED_ERRORS).
     */
    private handleError(error: HttpErrorResponse, request: HttpRequest<any>, next: HttpHandler, canRenewSession: boolean): Observable<HttpEvent<any>> {
        const resolved = this.apiErrorService.resolve(error, request);

        switch (resolved.behavior) {
            case 'renewSession':
                if (canRenewSession && !this.isRefresh(request)) {
                    return this.handle401Error(error, resolved, request, next);
                }
                this.endSession(resolved);
                break;
            case 'endSession':
                this.endSession(resolved);
                break;
            case 'forbiddenPage':
                this.notify(resolved);
                if (!resolved.handled) this.router.navigate(['forbidden']);
                break;
            case 'tenantSetup':
                // The tenant of the session has no schema (any more): the guard leads to the initial setup.
                this.injector.get(TenantStatusService).invalidate();
                this.router.navigate(['/']);
                break;
            case 'tenantFeatureDisabled':
                this.notify(resolved);
                this.injector
                    .get(TenantFeatureService)
                    .refresh(true)
                    .subscribe({ error: () => undefined });
                this.router.navigate(['/']);
                break;
            case 'notFoundPage':
                if (request.method === 'GET' && !resolved.handled) {
                    this.router.navigate(['notfound']);
                } else {
                    this.notify(resolved);
                }
                break;
            default:
                // Version conflicts included: ApiErrorService already titles them «Dati modificati».
                this.notify(resolved);
        }

        return throwError(() => error);
    }

    private notify(resolved: ResolvedApiError): void {
        if (!resolved.handled) {
            this.toastService.error(resolved.summary, resolved.detail);
        }
    }

    /** Shows why the session ends and logs out, once even when several requests fail together. */
    private endSession(resolved: ResolvedApiError): void {
        if (this.endingSession) return;
        this.endingSession = true;
        this.notify(resolved);
        this.keycloakService.logout();
    }

    private _isFreeRoute(url: string): boolean {
        for (const test of this._nonProtectedRoutes) {
            if (typeof test === 'string') {
                if (url === test) {
                    return true;
                }
                continue;
            }

            if (test.test(url)) {
                return true;
            }
        }

        return false;
    }

    private getAuthToken(): string | undefined {
        return this.keycloakService.token;
    }

    private addTokenHeader(request: HttpRequest<any>, token: string | undefined | null) {
        return token && !this._isFreeRoute(request.url) ? request.clone({ headers: request.headers.set('Authorization', `Bearer ${token}`) }) : request;
    }

    private isRefresh(request: HttpRequest<any>): boolean {
        return request.url.includes('/api/auth/token');
    }

    private handle401Error(error: HttpErrorResponse, resolved: ResolvedApiError, request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        if (!this.isRefreshing) {
            this.isRefreshing = true;
            this.refreshTokenSubject.next(null);

            const token = this.getAuthToken();
            const renewed = token ? this.keycloakService.refreshToken() : undefined;
            this.isRefreshing = false;

            if (!renewed) {
                // Without a token to retry with the request would wait forever: the session cannot be renewed.
                this.endSession(resolved);
                return throwError(() => error);
            }

            this.refreshTokenSubject.next(renewed);
            // return this.keycloakService.refreshToken().pipe(switchMap((token: AccessTokenResponse) => {
            //     this.isRefreshing = false;
            //     this.refreshTokenSubject.next(this.keycloakService.refreshToken());

            //     return next.handle(this.addTokenHeader(request, this.keycloakService.refreshToken()));
            // }), catchError((error) => {
            //     this.isRefreshing = false;

            //     return throwError(() => {
            //         this.keycloakService.logout();
            //     });
            // }));
        }

        return this.refreshTokenSubject.pipe(
            filter((token) => token),
            take(1),
            switchMap((token) => next.handle(this.addTokenHeader(request, token))),
            // A second 401 means the renewal did not help: the session ends instead of retrying again.
            catchError((retryError: HttpErrorResponse) => this.apiErrorService.readBody(retryError).pipe(switchMap((readable) => this.handleError(readable, request, next, false))))
        );
    }
}
