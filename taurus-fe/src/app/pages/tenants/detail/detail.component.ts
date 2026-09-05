import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { delay, finalize, first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import { ChildrenEntities, Tenants } from '../../../module';
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
    private originalFinanceEnabled = true;
    private originalInventoryEnabled = true;

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
                            this.toastService.success('Successo', 'Tenant eliminato');
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
                            this.toastService.success('Successo', 'Tenant eliminato definitivamente ai sensi del GDPR');
                            this.router.navigate(['/tenants']);
                        }
                    });
            }
        });
    }

    public save(): void {
        const disabled: string[] = [];
        if (this.originalFinanceEnabled && !this.tenant.financeEnabled) disabled.push('Economia');
        if (this.originalInventoryEnabled && !this.tenant.inventoryEnabled) disabled.push('Inventario');
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
                    this.toastService.success('Successo', 'Tenant aggiornato con successo');
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
        return !!this.tenant.id && this.tenant.active !== false && this.tenant.code === this.keycloakService.currentUserTenantCode;
    }

    private loadElement(id: number | string) {
        this.tenantsService
            .getById(Number(id))
            .pipe(first())
            .subscribe({
                next: (tenant: Tenants) => {
                    this.tenant = tenant;
                    this.tenant.financeEnabled ??= true;
                    this.tenant.inventoryEnabled ??= true;
                    this.originalFinanceEnabled = this.tenant.financeEnabled;
                    this.originalInventoryEnabled = this.tenant.inventoryEnabled;
                    this.tenant.country = this.tenant.country?.toUpperCase();
                    this.tenant.expireDate = this.dateConverterPipe.transform(this.tenant.expireDate);
                    this.isDirty = false;
                }
            });
    }
}
