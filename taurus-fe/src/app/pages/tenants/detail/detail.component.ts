import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { delay, finalize, first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import { ChildrenEntities, TenantFeature, Tenants } from '../../../module';
import { DateConverterPipe } from '../../../pipe';
import { ConfirmService, KeycloakService, TenantFeatureService, TenantsService, ToastService } from '../../../service';

@Component({
    selector: 'app-tenant-detail',
    imports: [ImportsModule],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [TenantsService]
})
export class DetailComponent extends DetailPageBase implements OnInit {
    public sortOptions!: SelectItem[];
    public totalRecords: number = 0;
    public tenant: Tenants = new Tenants();
    public cols: string[];
    public selectedTracks: ChildrenEntities[];
    protected readonly tenantFeatures: { key: TenantFeatureField; feature: TenantFeature; label: string; description: string; dependency?: TenantFeatureField }[] = [
        { key: 'financeEnabled', feature: TenantFeature.FINANCE, label: 'Economia', description: 'Conti, movimenti, rendiconti e informazioni economiche degli eventi.' },
        { key: 'inventoryEnabled', feature: TenantFeature.INVENTORY, label: 'Inventario', description: 'Oggetti, assegnazioni, fotografie, riconsegne e report.' },
        { key: 'onboardingImportEnabled', feature: TenantFeature.ONBOARDING_IMPORT, label: 'Onboarding e importazione iniziale', description: 'Wizard, template e importazioni iniziali del tenant.' },
        { key: 'externalCalendarFeedEnabled', feature: TenantFeature.EXTERNAL_CALENDAR_FEED, label: 'Feed calendario esterno', description: 'Feed personali e condivisi in formato iCalendar.' },
        { key: 'eventPreparationEnabled', feature: TenantFeature.EVENT_PREPARATION, label: 'Preparazione evento', description: 'Workspace operativo, indicatori, materiali e follow-up.' },
        { key: 'inventoryQrEnabled', feature: TenantFeature.INVENTORY_QR, label: 'QR code inventario', description: 'Etichette, scansione, azioni rapide e segnalazioni.', dependency: 'inventoryEnabled' },
        { key: 'notificationPreferencesEnabled', feature: TenantFeature.NOTIFICATION_PREFERENCES, label: 'Preferenze notifiche', description: 'Categorie, canali, digest, pausa e ore silenziose.' },
        { key: 'webPushRemindersEnabled', feature: TenantFeature.WEB_PUSH_REMINDERS, label: 'Promemoria eventi Web Push', description: 'Pianificazione e invio dei promemoria prima degli eventi.', dependency: 'notificationPreferencesEnabled' }
    ];
    private originalFeatureValues: Partial<Record<TenantFeatureField, boolean>> = {};

    constructor(
        private readonly tenantsService: TenantsService,
        private readonly toastService: ToastService,
        private readonly dateConverterPipe: DateConverterPipe,
        private readonly routeService: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmService: ConfirmService,
        private readonly keycloakService: KeycloakService,
        private readonly tenantFeatureService: TenantFeatureService
    ) {
        super();
        this.cols = ['Codice', 'Ordine', 'Nome'];
        this.selectedTracks = [];
    }

    ngOnInit() {
        this.tenantFeatureService.loadCapabilities().subscribe({ error: () => undefined });
        this.routeService.params.pipe(first()).subscribe((params) => {
            this.loadElement(params['id']);
        });
    }

    public confirmDelete(): void {
        this.confirmService.confirmReversible({
            title: 'Archivia tenant',
            consequence: 'Il tenant verrà archiviato, non eliminato, e potrà essere recuperato.',
            actionLabel: 'Archivia',
            accept: () => {
                this.tenantsService
                    .delete(this.tenant.id)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.isDirty = false;
                            this.toastService.success('Tenant eliminato', 'L’istanza non è più visibile.');
                            this.router.navigate(['/tenants']);
                        }
                    });
            }
        });
    }

    public confirmGdprDeletion(): void {
        this.confirmService.confirmDestructive({
            title: 'Cancellazione definitiva GDPR',
            consequence: 'Tutti i dati, gli indici, i file e le autorizzazioni del tenant verranno eliminati fisicamente e non potranno essere recuperati.',
            actionLabel: 'Elimina definitivamente',
            accept: () => {
                this.tenantsService
                    .deleteForGdpr(this.tenant.id)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.isDirty = false;
                            this.toastService.success('Dati del tenant eliminati', 'La cancellazione prevista dal GDPR è stata completata.');
                            this.router.navigate(['/tenants']);
                        }
                    });
            }
        });
    }

    public save(): void {
        const disabled = this.tenantFeatures.filter((item) => this.originalFeatureValues[item.key] === true && this.tenant[item.key] !== true).map((item) => item.label);
        if (this.originalFeatureValues.inventoryEnabled && this.tenant.inventoryEnabled !== true && this.tenant.inventoryQrEnabled === true && !disabled.includes('QR code inventario')) disabled.push('QR code inventario (per dipendenza)');
        if (this.originalFeatureValues.notificationPreferencesEnabled && this.tenant.notificationPreferencesEnabled !== true && this.tenant.webPushRemindersEnabled === true && !disabled.includes('Promemoria eventi Web Push'))
            disabled.push('Promemoria eventi Web Push (per dipendenza)');
        if (disabled.length) {
            this.confirmService.confirmReversible({
                title: 'Disattiva funzionalità',
                consequence: `${disabled.join(' e ')} non sarà più accessibile agli utenti di questo tenant. Menu, pagine, operazioni e notifiche collegate verranno nascosti o bloccati; tutti i dati saranno conservati.`,
                actionLabel: 'Disattiva e salva',
                accept: () => this.persist()
            });
            return;
        }
        this.persist();
    }

    private persist(): void {
        this.saving = true;
        this.tenantsService
            .update(this.tenant.id, this.tenant)
            .pipe(
                delay(1000),
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (tenant: Tenants) => {
                    this.isDirty = false;
                    this.toastService.success('Tenant aggiornato', 'Le modifiche sono state salvate.');
                    if (tenant.code === this.keycloakService.currentUserTenantCode) {
                        this.tenantFeatureService.refresh(true).subscribe({ error: () => undefined });
                    }
                    this.loadElement(tenant.id);
                }
            });
    }

    public onCountryChange(country: string | undefined): void {
        this.tenant.country = country ? country.toUpperCase() : undefined;
        this.isDirty = true;
    }

    public get countryInvalid(): boolean {
        return !!this.tenant.country && !/^[A-Z]{2}$/.test(this.tenant.country);
    }

    public get logoUrlInvalid(): boolean {
        if (!this.tenant.logoUrl) {
            return false;
        }

        try {
            const logoUrl = new URL(this.tenant.logoUrl);
            return logoUrl.protocol !== 'http:' && logoUrl.protocol !== 'https:';
        } catch {
            return true;
        }
    }

    public get timeZoneInvalid(): boolean {
        if (!this.tenant.timeZone) return true;
        try {
            new Intl.DateTimeFormat('it-IT', { timeZone: this.tenant.timeZone }).format();
            return false;
        } catch {
            return true;
        }
    }

    public get canConfigureData(): boolean {
        return !!this.tenant.id && this.tenant.active !== false && this.tenant.code === this.keycloakService.currentUserTenantCode && this.tenantFeatureService.onboardingImportEnabled();
    }

    protected featureStatus(item: { feature: TenantFeature; dependency?: TenantFeatureField }): string | undefined {
        const capability = this.tenantFeatureService.capabilities()?.[item.feature];
        if (capability && !capability.available) return 'Non disponibile nell’installazione';
        if (item.dependency && this.tenant[item.dependency] !== true) return 'Richiede un’altra funzionalità';
        return 'Disponibile';
    }

    private loadElement(id: number | string) {
        this.loading = true;
        this.tenantsService
            .getById(Number(id))
            .pipe(first(), finalize(() => (this.loading = false)))
            .subscribe({
                next: (tenant: Tenants) => {
                    this.tenant = tenant;
                    for (const item of this.tenantFeatures) {
                        this.tenant[item.key] ??= false;
                        this.originalFeatureValues[item.key] = this.tenant[item.key];
                    }
                    this.tenant.country = this.tenant.country?.toUpperCase();
                    this.tenant.expireDate = this.dateConverterPipe.transform(this.tenant.expireDate);
                    this.isDirty = false;
                }
            });
    }
}

type TenantFeatureField = 'financeEnabled' | 'inventoryEnabled' | 'onboardingImportEnabled' | 'externalCalendarFeedEnabled' | 'inventoryQrEnabled' | 'notificationPreferencesEnabled' | 'webPushRemindersEnabled' | 'eventPreparationEnabled';
