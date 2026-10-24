import { ApplicationRef } from '@angular/core';
import { SwUpdate, VersionEvent, VersionReadyEvent } from '@angular/service-worker';
import { MessageService } from 'primeng/api';
import { of, Subject } from 'rxjs';
import { AppUpdateService } from './app-update.service';

const versionReady = (): VersionReadyEvent => ({
    type: 'VERSION_READY',
    currentVersion: { hash: 'old' },
    latestVersion: { hash: 'new' }
});

describe('AppUpdateService', () => {
    let versionUpdates: Subject<VersionEvent>;
    let unrecoverable: Subject<{ type: 'UNRECOVERABLE_STATE'; reason: string }>;
    let activateUpdate: jasmine.Spy;
    let checkForUpdate: jasmine.Spy;
    let messageService: jasmine.SpyObj<MessageService>;
    let reloads: number;

    const service = (isEnabled: boolean): AppUpdateService => {
        const swUpdate = { isEnabled, versionUpdates, unrecoverable, activateUpdate, checkForUpdate } as unknown as SwUpdate;
        const appRef = { isStable: of(true) } as unknown as ApplicationRef;
        const created = new AppUpdateService(swUpdate, appRef, messageService);
        // Il reload vero ricaricherebbe la pagina del runner: qui se ne conta soltanto la richiesta.
        (created as unknown as { reload: () => void }).reload = () => reloads++;

        return created;
    };

    beforeEach(() => {
        versionUpdates = new Subject<VersionEvent>();
        unrecoverable = new Subject<{ type: 'UNRECOVERABLE_STATE'; reason: string }>();
        activateUpdate = jasmine.createSpy('activateUpdate').and.returnValue(Promise.resolve(true));
        checkForUpdate = jasmine.createSpy('checkForUpdate').and.returnValue(Promise.resolve(true));
        messageService = jasmine.createSpyObj<MessageService>('MessageService', ['add', 'clear']);
        reloads = 0;
    });

    it('stays inert when the service worker is disabled', () => {
        service(false).init();

        expect(versionUpdates.observed).toBeFalse();
        expect(unrecoverable.observed).toBeFalse();
        expect(checkForUpdate).not.toHaveBeenCalled();
    });

    it('checks for an update as soon as the application is stable', () => {
        service(true).init();

        expect(checkForUpdate).toHaveBeenCalledTimes(1);
    });

    it('activates the downloaded version right away in development', async () => {
        service(true).init();

        versionUpdates.next(versionReady());
        await activateUpdate.calls.mostRecent().returnValue;

        expect(activateUpdate).toHaveBeenCalled();
        expect(messageService.add).not.toHaveBeenCalled();
        expect(reloads).toBe(1);
    });

    it('ignores the version events that are not ready', () => {
        service(true).init();

        versionUpdates.next({ type: 'VERSION_DETECTED', version: { hash: 'new' } });

        expect(activateUpdate).not.toHaveBeenCalled();
        expect(reloads).toBe(0);
    });

    it('reloads without asking when the worker reaches an unrecoverable state', () => {
        service(true).init();

        unrecoverable.next({ type: 'UNRECOVERABLE_STATE', reason: 'missing files' });

        expect(activateUpdate).not.toHaveBeenCalled();
        expect(reloads).toBe(1);
    });

    it('clears its own toast and reloads even when the activation fails', async () => {
        activateUpdate.and.returnValue(Promise.reject(new Error('activation failed')));
        const created = service(true);

        created.activate();
        await activateUpdate.calls.mostRecent().returnValue.catch(() => undefined);

        expect(messageService.clear).toHaveBeenCalledWith(AppUpdateService.TOAST_KEY);
        expect(reloads).toBe(1);
    });
});
