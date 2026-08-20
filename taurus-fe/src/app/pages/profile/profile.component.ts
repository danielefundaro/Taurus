import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import Keycloak, { KeycloakProfile } from 'keycloak-js';
import { ConfirmationService } from 'primeng/api';
import { first } from 'rxjs';
import { HasUnsavedChanges } from '../../guard';
import { ImportsModule } from '../../imports';
import { KeycloakService, ToastService, UsersService } from '../../service';
import { CalendarEventsTableComponent } from "../../components/calendar-events-table/calendar-events-table.component";
import { InventoryAssignmentsComponent } from '../../components/inventory-assignments/inventory-assignments.component';

@Component({
    selector: 'app-profile',
    standalone: true,
    imports: [ImportsModule, CalendarEventsTableComponent, InventoryAssignmentsComponent],
    templateUrl: './profile.component.html',
    styleUrl: './profile.component.scss',
    providers: [ConfirmationService, UsersService],
    changeDetection: ChangeDetectionStrategy.Default,
})
export class ProfileComponent implements OnInit, HasUnsavedChanges {
    private readonly keycloak = inject(Keycloak);

    protected firstName: string = '';
    protected lastName: string = '';
    protected email: string = '';
    protected username: string = '';
    protected saving: boolean = false;
    isDirty = false;

    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly usersService: UsersService,
        private readonly confirmationService: ConfirmationService,
        private readonly toastService: ToastService,
    ) {}

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
        this.usersService.partialUpdateOwn({
            name: this.firstName,
            lastName: this.lastName,
            email: this.email,
        }).pipe(first()).subscribe({
            next: () => {
                this.saving = false;
                this.isDirty = false;
                this.toastService.success('Profilo aggiornato', 'Le modifiche sono state salvate con successo.');
            },
            error: () => {
                this.saving = false;
            },
        });
    }

    protected changePassword(): void {
        this.keycloak.login({ action: 'UPDATE_PASSWORD' });
    }

    protected configureOTP(): void {
        this.keycloak.login({ action: 'CONFIGURE_TOTP' });
    }

    protected logout(): void {
        this.confirmationService.confirm({
            header: 'Logout',
            message: "Sei sicuro di voler uscire?",
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Logout',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary', outlined: true },
            accept: () => this.keycloakService.logout(),
        });
    }

    protected confirmDeleteAccount(): void {
        this.confirmationService.confirm({
            header: 'Disattiva account',
            message: "Vuoi disattivare il tuo account?",
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Disattiva',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'warn' },
            rejectButtonProps: { severity: 'secondary', outlined: true },
            accept: () => this.deleteAccount(),
        });
    }

    protected confirmGdprDeletion(): void {
        this.confirmationService.confirm({
            header: 'Cancellazione definitiva GDPR',
            message: "La cancellazione ai sensi del GDPR è definitiva e irreversibile.</br>L'account e i dati personali verranno eliminati fisicamente e non sarà possibile recuperarli in futuro.</br>Vuoi procedere?",
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina definitivamente',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary', outlined: true },
            accept: () => this.deleteAccountForGdpr(),
        });
    }

    private deleteAccount(): void {
        this.usersService.deleteOwn().pipe(first()).subscribe({
            next: () => this.keycloakService.logout(),
            error: () => {
                this.toastService.error('Errore', "Impossibile eliminare l'account.");
            },
        });
    }

    private deleteAccountForGdpr(): void {
        this.usersService.deleteOwnForGdpr().pipe(first()).subscribe({
            next: () => this.keycloakService.logout(),
            error: () => this.toastService.error('Errore', "Impossibile completare la cancellazione definitiva dell'account."),
        });
    }
}
