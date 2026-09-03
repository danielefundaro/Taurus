import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import Keycloak, { KeycloakProfile } from 'keycloak-js';
import { first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { DetailPageBase } from '../_shared/detail-page.base';
import { ConfirmService, KeycloakService, PushNotificationService, ToastService, UserPreferenceService, UsersService } from '../../service';
import { CalendarEventsTableComponent } from '../../components/calendar-events-table/calendar-events-table.component';

@Component({
    selector: 'app-profile',
    standalone: true,
    imports: [ImportsModule, CalendarEventsTableComponent],
    templateUrl: './profile.component.html',
    styleUrl: './profile.component.scss',
    providers: [UsersService],
    changeDetection: ChangeDetectionStrategy.Default
})
export class ProfileComponent extends DetailPageBase implements OnInit {
    private static readonly REMINDER_KEY = 'defaultReminderMinutes';

    private readonly keycloak = inject(Keycloak);

    protected firstName: string = '';
    protected lastName: string = '';
    protected email: string = '';
    protected username: string = '';

    /** Notifiche: consenso del browser e promemoria predefinito degli eventi. */
    protected pushEnabled = false;
    protected pushBusy = false;
    protected defaultReminderMinutes = 30;
    protected savingReminder = false;

    private savedReminderMinutes = 30;

    protected readonly reminderUnit = 'notifiche';

    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly usersService: UsersService,
        private readonly confirmService: ConfirmService,
        private readonly toastService: ToastService,
        private readonly pushNotificationService: PushNotificationService,
        private readonly userPreferenceService: UserPreferenceService
    ) {
        super();
    }

    ngOnInit(): void {
        this.keycloakService.loadUserProfile().then((profile: KeycloakProfile) => {
            this.firstName = profile.firstName ?? '';
            this.lastName = profile.lastName ?? '';
            this.email = profile.email ?? '';
            this.username = profile.username ?? '';
        });

        this.pushNotificationService.refreshState().then((subscribed) => (this.pushEnabled = subscribed));

        this.userPreferenceService
            .get(ProfileComponent.REMINDER_KEY)
            .pipe(first())
            .subscribe((value) => {
                const minutes = Number(value);
                this.savedReminderMinutes = Number.isFinite(minutes) && value !== undefined ? minutes : 30;
                this.defaultReminderMinutes = this.savedReminderMinutes;
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

    protected get reminderDirty(): boolean {
        return this.defaultReminderMinutes !== this.savedReminderMinutes;
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

    protected onReminderChange(): void {
        this.setUnitDirty(this.reminderUnit, this.reminderDirty);
    }

    protected saveReminder(): void {
        this.savingReminder = true;
        this.userPreferenceService
            .set(ProfileComponent.REMINDER_KEY, String(this.defaultReminderMinutes))
            .pipe(first())
            .subscribe({
                next: () => {
                    this.savedReminderMinutes = this.defaultReminderMinutes;
                    this.savingReminder = false;
                    this.setUnitDirty(this.reminderUnit, false);
                    this.toastService.success('Promemoria aggiornato', 'Il nuovo anticipo vale per gli eventi senza promemoria personalizzato.');
                },
                error: () => {
                    this.savingReminder = false;
                    this.toastService.error('Salvataggio non riuscito', 'Non è stato possibile salvare il promemoria predefinito.');
                }
            });
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
