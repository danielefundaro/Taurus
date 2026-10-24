import { SwPush } from '@angular/service-worker';
import { of, throwError } from 'rxjs';
import { PushNotificationService } from './push-notification.service';
import { PushSubscriptionService } from './push-subscription.service';

const subscription = (endpoint: string): PushSubscription =>
    ({
        endpoint,
        unsubscribe: () => Promise.resolve(true)
    }) as unknown as PushSubscription;

describe('PushNotificationService', () => {
    let requestSubscription: jasmine.Spy;
    let swUnsubscribe: jasmine.Spy;
    let backend: jasmine.SpyObj<PushSubscriptionService>;

    const service = (isEnabled: boolean, existing: PushSubscription | null = null): PushNotificationService => {
        const swPush = { isEnabled, requestSubscription, unsubscribe: swUnsubscribe } as unknown as SwPush;
        const created = new PushNotificationService(swPush, backend);
        // La sottoscrizione del browser non è raggiungibile dal runner: si sostituisce la sola lettura.
        (created as unknown as { currentSubscription: () => Promise<PushSubscription | null> }).currentSubscription = () => Promise.resolve(existing);

        return created;
    };

    beforeEach(() => {
        requestSubscription = jasmine.createSpy('requestSubscription').and.returnValue(Promise.resolve(subscription('https://push.example/abc')));
        swUnsubscribe = jasmine.createSpy('unsubscribe').and.returnValue(Promise.resolve());
        backend = jasmine.createSpyObj<PushSubscriptionService>('PushSubscriptionService', ['subscribe', 'unsubscribe']);
        backend.subscribe.and.returnValue(of(undefined));
        backend.unsubscribe.and.returnValue(of(undefined));
    });

    it('reports the push as unsupported when the service worker is disabled', () => {
        const disabled = service(false);

        expect(disabled.supported).toBeFalse();
        expect(disabled.permission).toBe('unsupported');
    });

    it('mirrors the browser permission when the push is supported', () => {
        spyOnProperty(Notification, 'permission', 'get').and.returnValue('default');

        expect(service(true).permission).toBe('default');
    });

    it('never asks for the permission at startup', async () => {
        spyOnProperty(Notification, 'permission', 'get').and.returnValue('default');

        await service(true).init();

        expect(requestSubscription).not.toHaveBeenCalled();
        expect(backend.subscribe).not.toHaveBeenCalled();
    });

    it('realigns the backend at startup when the browser is already subscribed', async () => {
        spyOnProperty(Notification, 'permission', 'get').and.returnValue('granted');
        const existing = subscription('https://push.example/known');
        const created = service(true, existing);

        await created.init();

        expect(requestSubscription).not.toHaveBeenCalled();
        expect(backend.subscribe).toHaveBeenCalledWith(existing);
        expect(created.subscribed()).toBeTrue();
    });

    it('refuses to subscribe when the permission is denied', async () => {
        spyOnProperty(Notification, 'permission', 'get').and.returnValue('denied');

        await expectAsync(service(true).requestPermissionAndSubscribe()).toBeResolvedTo(false);
        expect(requestSubscription).not.toHaveBeenCalled();
    });

    it('registers on the backend the subscription granted by the browser', async () => {
        spyOnProperty(Notification, 'permission', 'get').and.returnValue('default');
        const created = service(true);

        await expectAsync(created.requestPermissionAndSubscribe()).toBeResolvedTo(true);
        expect(created.subscribed()).toBeTrue();
        expect(backend.subscribe).toHaveBeenCalled();
    });

    it('keeps the state clean when the browser refuses the subscription', async () => {
        spyOnProperty(Notification, 'permission', 'get').and.returnValue('default');
        requestSubscription.and.returnValue(Promise.reject(new Error('rifiutata')));
        const created = service(true);

        await expectAsync(created.requestPermissionAndSubscribe()).toBeResolvedTo(false);
        expect(created.subscribed()).toBeFalse();
        expect(backend.subscribe).not.toHaveBeenCalled();
    });

    it('stays subscribed when the backend call fails', async () => {
        spyOnProperty(Notification, 'permission', 'get').and.returnValue('default');
        backend.subscribe.and.returnValue(throwError(() => new Error('rete')));
        const created = service(true);

        await created.requestPermissionAndSubscribe();

        expect(created.subscribed()).toBeTrue();
    });

    it('disables the push on the backend with the endpoint of the device', async () => {
        const created = service(true, subscription('https://push.example/device'));

        await created.unsubscribe();

        expect(swUnsubscribe).toHaveBeenCalled();
        expect(backend.unsubscribe).toHaveBeenCalledWith('https://push.example/device');
        expect(created.subscribed()).toBeFalse();
    });

    it('does not call the backend when the device has no subscription', async () => {
        const created = service(true);

        await created.unsubscribe();

        expect(backend.unsubscribe).not.toHaveBeenCalled();
        expect(created.subscribed()).toBeFalse();
    });

    it('aligns the state with the subscription really present in the browser', async () => {
        await expectAsync(service(true, subscription('https://push.example/abc')).refreshState()).toBeResolvedTo(true);
        await expectAsync(service(true).refreshState()).toBeResolvedTo(false);
        await expectAsync(service(false, subscription('https://push.example/abc')).refreshState()).toBeResolvedTo(false);
    });
});
