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
            let detail = "Errore durante la richiesta";

            if (error.status === 401 && !this.isRefresh(authReq)) {
                return this.handle401Error(authReq, next);
            } else if (error.status === 403) {
                this.router.navigate(['forbidden']);
            } else if ('error' in error) {
                if ('message' in error.error) {
                    const message = error.error.message.replace("error.", '').toLowerCase();
                    
                    switch (message) {
                        case 'id.notfound': detail = 'Elemento non trovato'; break;
                        case 'id.exists': detail = 'Elemento già esistente'; break;
                        case 'id.null':
                        case 'id.invalid': detail = 'Elemento non valido'; break;
                        case 'send.message':
                        case 'file.upload': detail = 'Errore durante l\'upload del file'; break;
                        case 'save.tenant': detail = 'Errore durante il salvataggio del tenant'; break;
                        case 'code.exists': detail = 'Codice già esistente'; break;
                        // Inizio errori su keycloak
                        case 'get.users':
                        case 'users.list': detail = 'Errore durante il recupero della lista utenti'; break;
                        case 'get.user': detail = 'Errore durante il recupero dell\'utente'; break;
                        case 'save.user': detail = 'Errore durante il salvataggio dell\'utente'; break;
                        case 'update.user': detail = 'Errore durante l\'aggiornamento dell\'utente'; break;
                        case 'delete.user': detail = 'Errore durante l\'eliminazione dell\'utente'; break;
                        case 'send.user.reset.password': detail = 'Errore durante l\'invio dell\'email per il reset della password'; break;
                        case 'send.user.verify.email': detail = 'Errore durante l\'invio dell\'email per la verifica dell\'account'; break;
                        case 'save.group': detail = 'Errore durante il salvataggio del tenant'; break;
                        case 'get.group': detail = 'Errore durante il recupero del tenant'; break;
                        case 'get.client':
                        case 'update.user.group':
                        case 'get.user.roles':
                        case 'save.user.roles':
                        case 'delete.user.roles':
                        case 'get.client.roles':
                        case 'token.error':
                        case 'token.invalid':
                        case 'conflict.error':
                        case 'credentials.error':
                        case 'generic.error': detail = 'Errore durante l\'operazione. Contattare l\'amministratore'; break;
                        // Fine errori su keycloak
                    }

                    this.toastService.error('Errore', detail);

                    if (message === 'id.notfound') {
                        this.router.navigate(["notfound"]);
                    }
                } else {
                    this.toastService.error('Errore', detail);
                }
            } else {
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
