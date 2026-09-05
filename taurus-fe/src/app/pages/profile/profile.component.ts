import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import Keycloak, { KeycloakProfile } from 'keycloak-js';
import { first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { DetailPageBase } from '../_shared/detail-page.base';
import { ConfirmService, KeycloakService, NotificationPreferencesService, PushNotificationService, ToastService, UsersService } from '../../service';
import { CalendarEventsTableComponent } from '../../components/calendar-events-table/calendar-events-table.component';
import { CalendarFeedPanelComponent } from '../../components/calendar-feed-panel/calendar-feed-panel.component';
import { NotificationCategoryPreference, NotificationPreferences, NotificationPushMode, NotificationSource } from '../../module';

@Component({
    selector: 'app-profile',
    standalone: true,
    imports: [ImportsModule, CalendarEventsTableComponent, CalendarFeedPanelComponent],
    templateUrl: './profile.component.html',
    styleUrl: './profile.component.scss',
    providers: [UsersService],
    changeDetection: ChangeDetectionStrategy.Default
})
export class ProfileComponent extends DetailPageBase implements OnInit {
    private readonly keycloak = inject(Keycloak);

    protected firstName: string = '';
    protected lastName: string = '';
    protected email: string = '';
    protected username: string = '';

    /** Notifiche: consenso del browser e promemoria predefinito degli eventi. */
    protected pushEnabled = false;
    protected pushBusy = false;
    protected notificationPreferences?: NotificationPreferences;
    protected pushPausedUntil?: Date;
    protected savingNotifications = false;
    private savedNotificationPreferences?: NotificationPreferences;

    protected readonly pushModes: { label: string; value: NotificationPushMode }[] = [
        { label: 'Nessuno', value: 'OFF' },
        { label: 'Immediato', value: 'IMMEDIATE' },
        { label: 'Riepilogo giornaliero', value: 'DAILY_DIGEST' }
    ];
    protected readonly previewModes = [
        { label: 'Privata', value: 'PRIVATE' },
        { label: 'Completa', value: 'FULL' }
    ];
    protected readonly minPushPause = new Date(Date.now() + 5 * 60 * 1000);
    protected readonly maxPushPause = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    protected readonly categoryLabels: Record<NotificationSource, string> = {
        CALENDAR: 'Calendario',
        INVENTORY: 'Inventario',
        FINANCE: 'Economia',
        CONTENT: 'Contenuti',
        IDENTITY: 'Utenti e accessi',
        TENANT: 'Organizzazione',
        GENERAL: 'Generali'
    };

    protected readonly reminderUnit = 'notifiche';
    protected readonly calendarFeedVisible: boolean;

    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly usersService: UsersService,
        private readonly confirmService: ConfirmService,
        private readonly toastService: ToastService,
        private readonly pushNotificationService: PushNotificationService,
        private readonly notificationPreferencesService: NotificationPreferencesService
    ) {
        super();
        this.calendarFeedVisible = keycloakService.isUser || keycloakService.isUserExternal;
    }

    ngOnInit(): void {
        this.keycloakService.loadUserProfile().then((profile: KeycloakProfile) => {
            this.firstName = profile.firstName ?? '';
            this.lastName = profile.lastName ?? '';
            this.email = profile.email ?? '';
            this.username = profile.username ?? '';
        });

        this.pushNotificationService.refreshState().then((subscribed) => (this.pushEnabled = subscribed));

        this.notificationPreferencesService
            .getPreferences()
            .pipe(first())
            .subscribe({
                next: (value) => this.setNotificationPreferences(value),
                error: () => this.toastService.error('Preferenze non disponibili', 'Non è stato possibile caricare le preferenze di notifica.')
            });
    }

    protected get pushSupported(): boolean {
        return this.pushNotificationService.supported;
    }

    protected get pushDenied(): boolean {
        return this.pushNotificationService.permission === 'denied';
    }

    protected get pushHint(): string {
        if (!this.pushSupported) return 'Questo browser non supporta le notifiche push.';
        if (this.pushDenied) return 'Le notifiche sono bloccate nelle impostazioni del browser: sbloccale da lì per riattivarle.';
        return 'Il promemoria arriva anche ad applicazione chiusa, sui dispositivi su cui hai dato il consenso.';
    }

    protected get notificationPreferencesDirty(): boolean {
        return !!this.notificationPreferences && JSON.stringify(this.notificationPreferences) !== JSON.stringify(this.savedNotificationPreferences);
    }

    protected onPushToggle(enabled: boolean): void {
        if (this.pushBusy) return;
        this.pushBusy = true;
        const action = enabled ? this.pushNotificationService.requestPermissionAndSubscribe() : this.pushNotificationService.unsubscribe().then(() => false);

        Promise.resolve(action).then((result) => {
            this.pushBusy = false;
            this.pushEnabled = enabled ? result === true : false;
            if (enabled && !this.pushEnabled) {
                this.toastService.warn('Notifiche non attivate', 'Il browser non ha concesso il permesso di inviare notifiche.');
                return;
            }
            this.toastService.success('Notifiche aggiornate', this.pushEnabled ? 'Riceverai i promemoria degli eventi su questo dispositivo.' : 'Non riceverai più promemoria su questo dispositivo.');
        });
    }

    protected get usesDigest(): boolean {
        return this.notificationPreferences?.categories.some((category) => category.pushMode === 'DAILY_DIGEST') ?? false;
    }

    protected get digestInsideQuietHours(): boolean {
        const value = this.notificationPreferences;
        if (!value || !this.usesDigest || !value.quietHours.enabled) return false;
        const start = value.quietHours.start.slice(0, 5);
        const end = value.quietHours.end.slice(0, 5);
        const digest = value.digestLocalTime.slice(0, 5);
        return start < end ? digest >= start && digest < end : digest >= start || digest < end;
    }

    protected get notificationPreferencesInvalid(): boolean {
        const value = this.notificationPreferences;
        return (
            !value || !value.timeZone.trim() || value.defaultCalendarReminderMinutes < 0 || value.defaultCalendarReminderMinutes > 1440 || (value.quietHours.enabled && value.quietHours.start === value.quietHours.end) || this.digestInsideQuietHours
        );
    }

    protected notificationPreferenceChanged(): void {
        if (this.notificationPreferences) {
            this.notificationPreferences.pushPausedUntil = this.pushPausedUntil?.toISOString() ?? null;
        }
        this.setUnitDirty(this.reminderUnit, this.notificationPreferencesDirty);
    }

    protected categoryLabel(category: NotificationCategoryPreference): string {
        return this.categoryLabels[category.source] ?? category.source;
    }

    protected clearPushPause(): void {
        this.pushPausedUntil = undefined;
        this.notificationPreferenceChanged();
    }

    protected saveNotificationPreferences(): void {
        if (!this.notificationPreferences || this.notificationPreferencesInvalid) return;
        this.savingNotifications = true;
        this.notificationPreferencesService
            .savePreferences(this.notificationPreferences)
            .pipe(first())
            .subscribe({
                next: (value) => {
                    this.setNotificationPreferences(value);
                    this.savingNotifications = false;
                    this.setUnitDirty(this.reminderUnit, false);
                    this.toastService.success('Preferenze aggiornate', 'Le nuove regole verranno applicate alle prossime notifiche.');
                },
                error: (error) => {
                    this.savingNotifications = false;
                    if (error.status === 409) {
                        this.toastService.warn('Preferenze modificate altrove', 'Ricarica la pagina prima di salvare di nuovo.');
                    } else {
                        this.toastService.error('Salvataggio non riuscito', 'Non è stato possibile salvare le preferenze di notifica.');
                    }
                }
            });
    }

    private setNotificationPreferences(value: NotificationPreferences): void {
        value.quietHours.start = value.quietHours.start.slice(0, 5);
        value.quietHours.end = value.quietHours.end.slice(0, 5);
        value.digestLocalTime = value.digestLocalTime.slice(0, 5);
        this.notificationPreferences = this.clonePreferences(value);
        this.savedNotificationPreferences = this.clonePreferences(value);
        this.pushPausedUntil = value.pushPausedUntil ? new Date(value.pushPausedUntil) : undefined;
    }

    private clonePreferences(value: NotificationPreferences): NotificationPreferences {
        return JSON.parse(JSON.stringify(value)) as NotificationPreferences;
    }

    protected saveProfile(): void {
        this.saving = true;
        this.usersService
            .partialUpdateOwn({
                name: this.firstName,
                lastName: this.lastName,
                email: this.email
            })
            .pipe(first())
            .subscribe({
                next: () => {
                    this.saving = false;
                    this.isDirty = false;
                    this.toastService.success('Profilo aggiornato', 'Le modifiche sono state salvate con successo.');
                },
                error: () => {
                    this.saving = false;
                }
            });
    }

    protected changePassword(): void {
        this.keycloak.login({ action: 'UPDATE_PASSWORD' });
    }

    protected configureOTP(): void {
        this.keycloak.login({ action: 'CONFIGURE_TOTP' });
    }

    protected logout(): void {
        this.confirmService.confirmReversible({
            title: 'Esci da Taurus',
            consequence: 'La sessione corrente verrà terminata.',
            actionLabel: 'Esci',
            accept: () => this.keycloakService.logout()
        });
    }

    protected confirmDeleteAccount(): void {
        this.confirmService.confirmReversible({
            title: 'Disattiva account',
            consequence: 'Non potrai più accedere finché l’account non verrà riattivato.',
            actionLabel: 'Disattiva',
            accept: () => this.deleteAccount()
        });
    }

    protected confirmGdprDeletion(): void {
        this.confirmService.confirmDestructive({
            title: 'Cancellazione definitiva GDPR',
            consequence: 'L’account e i dati personali verranno eliminati fisicamente e non potranno essere recuperati.',
            actionLabel: 'Elimina definitivamente',
            accept: () => this.deleteAccountForGdpr()
        });
    }

    private deleteAccount(): void {
        this.usersService
            .deleteOwn()
            .pipe(first())
            .subscribe({
                next: () => this.keycloakService.logout(),
                error: () => {
                    this.toastService.error('Errore', "Impossibile eliminare l'account.");
                }
            });
    }

    private deleteAccountForGdpr(): void {
        this.usersService
            .deleteOwnForGdpr()
            .pipe(first())
            .subscribe({
                next: () => this.keycloakService.logout(),
                error: () => this.toastService.error('Errore', "Impossibile completare la cancellazione definitiva dell'account.")
            });
    }
}
