import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, SelectItem } from 'primeng/api';
import { delay, finalize, first } from 'rxjs';
import { HasUnsavedChanges } from '../../../guard';
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, Tenants } from '../../../module';
import { DateConverterPipe } from '../../../pipe';
import { TenantsService, ToastService } from '../../../service';

@Component({
    selector: 'app-tenant-detail',
    imports: [
        ImportsModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        TenantsService,
        ConfirmationService,
    ],
})
export class DetailComponent implements OnInit, HasUnsavedChanges {
    public sortOptions!: SelectItem[];
    public totalRecords: number = 0;
    public tenant: Tenants = new Tenants();
    public cols: string[];
    public selectedTracks: ChildrenEntities[];
    isDirty = false;
    isSaving = false;

    constructor(
        private readonly tenantsService: TenantsService,
        private readonly toastService: ToastService,
        private readonly dateConverterPipe: DateConverterPipe,
        private readonly routeService: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmationService: ConfirmationService,
    ) {
        this.cols = ["Codice", "Ordine", "Nome"];
        this.selectedTracks = [];
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    public confirmDelete(): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Eliminare definitivamente questo tenant?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.tenantsService.delete(this.tenant.id).pipe(first()).subscribe({
                    next: () => {
                        this.isDirty = false;
                        this.toastService.success('Successo', 'Tenant eliminato');
                        this.router.navigate(['/tenants']);
                    },
                });
            },
        });
    }

    public save(): void {
        this.isSaving = true;
        this.tenantsService.update(this.tenant.id, this.tenant).pipe(delay(1000), first(), finalize(() => this.isSaving = false)).subscribe({
            next: (tenant: Tenants) => {
                this.isDirty = false;
                this.toastService.success("Successo", "Tenant aggiornato con successo");
                this.loadElement(tenant.id);
            }
        });
    }

    private loadElement(id: string) {
        this.tenantsService.getById(id).pipe(first()).subscribe({
            next: (tenant: Tenants) => {
                this.tenant = tenant;
                this.tenant.expireDate = this.dateConverterPipe.transform(this.tenant.expireDate);
                this.isDirty = false;
            }
        });
    }
}
