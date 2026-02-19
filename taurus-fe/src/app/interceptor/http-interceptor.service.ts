import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, finalize, Observable, switchMap, throwError } from 'rxjs';
import { catchError, filter, take } from 'rxjs/operators';
import { KeycloakService, LoadingService, ToastService } from '../service';
import { Router } from '@angular/router';

@Injectable({
    providedIn: 'root',
})
export class HttpInterceptorService implements HttpInterceptor {
    private readonly _nonProtectedRoutes: (RegExp | string)[] = [
    ];
    private readonly excludedRoutes = ["api/preferences"];

    private readonly httpRequestStack = new Array<HttpRequest<any>>();
    private isRefreshing = false;
    private readonly refreshTokenSubject: BehaviorSubject<any>;

    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly toastService: ToastService,
        private readonly loadingService: LoadingService,
        private readonly router: Router,
    ) {
        this.refreshTokenSubject = new BehaviorSubject<any>(null);
    }

    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        if (this._isFreeRoute(req.url)) {
            return next.handle(req);
        }

        if (!this.keycloakService.isUserLoggedIn) {
            this.keycloakService.logout();
            return next.handle(req).pipe(
                catchError((error: HttpErrorResponse) => {
                    this.toastService.error('Errore', 'Errore durante la richiesta');
                    return throwError(() => error);
                }),
            );
        }

        if (!this.excludedRoutes.some(route => req.url.includes(route))) {
            this.loadingService.loading = true;
            this.httpRequestStack.push(req);
        }

        const token = this.getAuthToken();
        const authReq = this.addTokenHeader(req, token);

        return next.handle(authReq).pipe(finalize(() => {
            if (!this.excludedRoutes.some(route => authReq.url.includes(route))) {
                this.httpRequestStack.pop();
            }

            if (this.httpRequestStack.length === 0) {
                this.loadingService.loading = false;
            }
        }), catchError((error: HttpErrorResponse) => {
            if (error.status === 401 && !this.isRefresh(authReq)) {
                return this.handle401Error(authReq, next);
            } else if (error.status === 403) {
                this.router.navigate(['forbidden']);
            } else if ('error' in error) {
                if (error.error.message === 'error.id.notFound') {
                    this.router.navigate(["notfound"]);
                } else {
                    let detail = "Errore durante la richiesta";
                    this.toastService.error('Errore', detail);
                }
            } else {
                let detail = "Errore durante la richiesta";
                this.toastService.error('Errore', detail);
            }

            return throwError(() => error);
        }));
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
        return request.url.includes("/api/auth/token");
    }

    private handle401Error(request: HttpRequest<any>, next: HttpHandler) {
        if (!this.isRefreshing) {
            this.isRefreshing = true;
            this.refreshTokenSubject.next(null);

            const token = this.getAuthToken();

            if (token) {
                this.isRefreshing = false;
                this.refreshTokenSubject.next(this.keycloakService.refreshToken());
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
        }

        return this.refreshTokenSubject.pipe(
            filter(token => token),
            take(1),
            switchMap((token) => next.handle(this.addTokenHeader(request, token)))
        );
    }
}
