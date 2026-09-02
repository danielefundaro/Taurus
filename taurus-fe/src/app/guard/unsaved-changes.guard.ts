import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { ConfirmService } from '../service/confirm.service';
import { Observable, Subject } from 'rxjs';

export interface HasUnsavedChanges {
    isDirty: boolean;
    dirtyUnitLabels?: string[];
}

export const canDeactivateUnsavedChanges: CanDeactivateFn<HasUnsavedChanges> = (component): Observable<boolean> | boolean => {
    if (!component.isDirty) return true;

    const confirmService = inject(ConfirmService);
    const result$ = new Subject<boolean>();

    const units = component.dirtyUnitLabels?.length ? `: ${component.dirtyUnitLabels.join(', ')}` : '';
    confirmService.confirmDiscard({
        title: 'Modifiche non salvate',
        consequence: `Uscendo perderai le modifiche non salvate${units}.`,
        actionLabel: 'Esci senza salvare',
        accept: () => {
            result$.next(true);
            result$.complete();
        },
        reject: () => {
            result$.next(false);
            result$.complete();
        }
    });

    return result$.asObservable();
};
