import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, finalize, Observable, switchMap, throwError } from 'rxjs';
import { catchError, filter, take } from 'rxjs/operators';
import { KeycloakService } from '../service';

@Injectable({
    providedIn: 'root',
})
export class HttpInterceptorService implements HttpInterceptor {
    private readonly _nonProtectedRoutes: (RegExp | string)[] = [
    ];
    private readonly excludedRoutes = [];

    private readonly httpRequestStack = Array<HttpRequest<any>>();
    private isRefreshing = false;
    private readonly refreshTokenSubject: BehaviorSubject<any>;

    constructor(private readonly keycloakService: KeycloakService) {
        this.isRefreshing = false;
        this.refreshTokenSubject = new BehaviorSubject<any>(null);
    }

    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        if (this._isFreeRoute(req.url)) {
            return next.handle(req);
        }

        if (!this.keycloakService.$isUserLoggedIn.value) {
            this.keycloakService.logout();
            return next.handle(req).pipe(
                // catchError((error: HttpErrorResponse) => {
                //     this.messageService.add({
                //         severity: 'error',
                //         summary: 'Errore',
                //         detail: 'Errore durante la richiesta',
                //         life: 2000,
                //     });
                //     return throwError(() => error);
                // }),
            );
        }

        if (!this.excludedRoutes.some(route => req.url.includes(route))) {
          this.keycloakService.$loading.next(true);
          this.httpRequestStack.push(req);
        }

        const token = this.getAuthToken();
        const authReq = this.addTokenHeader(req, token);

        return next.handle(authReq).pipe(finalize(() => {
            this.httpRequestStack.pop();

            if (this.httpRequestStack.length === 0) {
                this.keycloakService.$loading.next(false);
            }
        }), catchError((error: HttpErrorResponse) => {
            if (error && error.status === 401 && !this.isRefresh(authReq)) {
                return this.handle401Error(authReq, next);
            } else if (error && error.status === 413 && error.url?.includes('/guidelines')) {
            //   this.messageService.add({
            //     severity: 'error',
            //     summary: 'Errore',
            //     detail: `Operazione non consentita: dimensione massima di 20MB superata`,
            //     life: 3000,
            //   });
            } else {
                // let detail = "Errore durante la richiesta";

                // if (error.error) {
                //     switch (error.error.message) {
                //         case "error.rowInvalidCrime": detail = "Errore durante la richiesta. Reato non valido"; break;
                //         case "error.rowInvalidSource": detail = "Errore durante la richiesta. Tipo fonte non valido"; break;
                //         case "error.rowInvalidUrl": detail = "Errore durante la richiesta. Url non valido"; break;
                //         case "error.nonempty":  detail = "Errore durante la richiesta. La categoria di reato è associata a dei reati"; break;
                //         case "error.existcustomizations": detail = "Operazione non consentita: a questo gestore sono associate delle personalizzati incomplete"; break;
                //         case "error.fileTooLarge": detail = `Operazione non consentita: dimensione massima di ${error.error.detail} superata` ; break;
                //         case "error.fileEmpty": detail = "Operazione non consentita: il file passato è vuoto"; break;
                //         case "error.fileNotAllowed": detail = "Operazione non consentita: non è possibile caricare questo tipo di file"; break;
                //     }
                // }

                // this.messageService.add({
                //     severity: 'error',
                //     summary: 'Errore',
                //     detail: detail,
                //     life: 3000,
                // });
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
