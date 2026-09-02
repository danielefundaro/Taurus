import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import Keycloak, { KeycloakProfile } from 'keycloak-js';
import { first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { DetailPageBase } from '../_shared/detail-page.base';
import { ConfirmService, KeycloakService, ToastService, UsersService } from '../../service';
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
    private readonly keycloak = inject(Keycloak);

    protected firstName: string = '';
    protected lastName: string = '';
    protected email: string = '';
    protected username: string = '';

    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly usersService: UsersService,
        private readonly confirmService: ConfirmService,
        private readonly toastService: ToastService
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
