import { Injectable, isDevMode } from '@angular/core';
import { SwPush } from '@angular/service-worker';
import { environment } from '../../environments/environment';
import { PushSubscriptionService } from './push-subscription.service';

@Injectable({ providedIn: 'root' })
export class PushNotificationService {

    constructor(
        private readonly swPush: SwPush,
        private readonly pushSubscriptionService: PushSubscriptionService,
    ) {}

    async init(): Promise<void> {
        console.debug('[Push] isEnabled:', this.swPush.isEnabled);
        if (!this.swPush.isEnabled) return;

        const vapidKey = (environment as any).vapidPublicKey;
        console.debug('[Push] vapidKey:', vapidKey?.substring(0, 10) + '...');
        if (!vapidKey || vapidKey === 'CHANGE_ME') return;

        try {
            const subscription = await this.swPush.requestSubscription({ serverPublicKey: vapidKey });
            console.debug('[Push] subscription obtained, saving to backend...');
            this.pushSubscriptionService.subscribe(subscription).subscribe({
                next: () => console.debug('[Push] subscription saved'),
                error: (e) => console.error('[Push] failed to save subscription', e),
            });
        } catch (e) {
            console.error('[Push] requestSubscription failed:', e);
        }
    }
}
