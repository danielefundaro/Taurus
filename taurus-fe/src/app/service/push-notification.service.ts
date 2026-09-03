import { Injectable, signal } from '@angular/core';
import { SwPush } from '@angular/service-worker';
import { first } from 'rxjs';
import { environment } from '../../environments/environment';
import { PushSubscriptionService } from './push-subscription.service';

export type PushPermission = 'default' | 'granted' | 'denied' | 'unsupported';

@Injectable({ providedIn: 'root' })
export class PushNotificationService {
    /** Vero quando il browser ha una sottoscrizione attiva registrata sul backend. */
    readonly subscribed = signal(false);

    constructor(
        private readonly swPush: SwPush,
        private readonly pushSubscriptionService: PushSubscriptionService
    ) {}

    get supported(): boolean {
        return this.swPush.isEnabled && !!this.vapidKey && typeof Notification !== 'undefined';
    }

    get permission(): PushPermission {
        if (!this.supported) return 'unsupported';
        return Notification.permission as PushPermission;
    }

    /**
     * All'avvio non si chiede mai il permesso: ci si limita a riallineare il
     * backend a una sottoscrizione che il browser ha già concesso. Il consenso
     * viene richiesto solo da un gesto esplicito dell'utente.
     */
    async init(): Promise<void> {
        if (!this.supported || Notification.permission !== 'granted') return;
        const existing = await this.currentSubscription();
        if (existing) {
            this.sendToBackend(existing);
            return;
        }
        await this.subscribe();
    }

    /** Chiede il permesso e registra la sottoscrizione. Restituisce l'esito. */
    async requestPermissionAndSubscribe(): Promise<boolean> {
        if (!this.supported) return false;
        if (Notification.permission === 'denied') return false;
        return this.subscribe();
    }

    async unsubscribe(): Promise<void> {
        const existing = await this.currentSubscription();
        if (!existing) {
            this.subscribed.set(false);
            return;
        }
        const endpoint = existing.endpoint;
        try {
            await this.swPush.unsubscribe();
        } catch {
            await existing.unsubscribe().catch(() => undefined);
        }
        this.subscribed.set(false);
        this.pushSubscriptionService
            .unsubscribe(endpoint)
            .pipe(first())
            .subscribe({ error: () => undefined });
    }

    /** Allinea `subscribed` allo stato reale del browser. */
    async refreshState(): Promise<boolean> {
        const existing = this.supported ? await this.currentSubscription() : null;
        this.subscribed.set(!!existing);
        return this.subscribed();
    }

    private get vapidKey(): string | undefined {
        const key = (environment as { vapidPublicKey?: string }).vapidPublicKey;
        return key && key !== 'CHANGE_ME' ? key : undefined;
    }

    private async subscribe(): Promise<boolean> {
        const serverPublicKey = this.vapidKey;
        if (!serverPublicKey) return false;
        try {
            const subscription = await this.swPush.requestSubscription({ serverPublicKey });
            this.sendToBackend(subscription);
            return true;
        } catch {
            this.subscribed.set(false);
            return false;
        }
    }

    private sendToBackend(subscription: PushSubscription): void {
        this.subscribed.set(true);
        this.pushSubscriptionService
            .subscribe(subscription)
            .pipe(first())
            .subscribe({ error: () => undefined });
    }

    private async currentSubscription(): Promise<PushSubscription | null> {
        if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return null;
        try {
            const registration = await navigator.serviceWorker.ready;
            return await registration.pushManager.getSubscription();
        } catch {
            return null;
        }
    }
}
