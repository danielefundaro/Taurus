import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { Observable, Subject } from 'rxjs';

export interface HasUnsavedChanges {
    isDirty: boolean;
}

export const canDeactivateUnsavedChanges: CanDeactivateFn<HasUnsavedChanges> = (component): Observable<boolean> | boolean => {
    if (!component.isDirty) return true;

    const confirmationService = inject(ConfirmationService);
    const result$ = new Subject<boolean>();

    confirmationService.confirm({
        key: 'guard',
        header: 'Modifiche non salvate',
        message: 'Ci sono modifiche non salvate. Uscire senza salvare?',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Esci senza salvare',
        rejectLabel: 'Rimani',
        acceptButtonProps: { severity: 'warning' },
        rejectButtonProps: { severity: 'secondary' },
        accept: () => { result$.next(true); result$.complete(); },
        reject: () => { result$.next(false); result$.complete(); },
    });

    return result$.asObservable();
};
