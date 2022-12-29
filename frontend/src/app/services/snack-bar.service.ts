import { Injectable } from '@angular/core';
import { MatSnackBar, MatSnackBarConfig } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
    providedIn: 'root'
})
export class SnackBarService {
    constructor(private snackBar: MatSnackBar, private router: Router, private translateService: TranslateService) { }

    info(message: string, duration: number = 2000): void {
        this.open(message, duration, ['mat-snackbar', 'info']);
    }

    success(message: string, duration: number = 2000): void {
        this.open(message, duration, ['mat-snackbar', 'success']);
    }

    warning(message: string, duration: number = 2000): void {
        this.open(message, duration, ['mat-snackbar', 'warn']);
    }

    error(message: string, status?: number, duration: number = 5000): void {
        if (status == 403) {
            message = message.replace(/\[.*\]/, "") + this.translateService.instant("SHARED.FORBIDDEN");
        }

        this.open(message, duration, ['mat-snackbar', 'error']);

        if (status == 404) {
            this.router.navigate(['404']);
        }
    }

    private open(message: string, duration?: number, paneClass?: string | string[]) {
        MatSnackBarConfig
        this.snackBar.open(message, '', {
            duration: duration,
            horizontalPosition: 'right',
            verticalPosition: 'top',
            panelClass: paneClass
        });
    }
}
