import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { delay, first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, Tenants } from '../../../module';
import { TenantsService, ToastService } from '../../../service';

@Component({
    selector: 'app-tenant-detail',
    imports: [
        ImportsModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        TenantsService
    ],
})
export class DetailComponent implements OnInit {
    public sortOptions!: SelectItem[];
    public totalRecords: number = 0;
    public tenant: Tenants = new Tenants();
    public cols: string[];
    public selectedTracks: ChildrenEntities[];

    constructor(
        private readonly tenantsService: TenantsService,
        private readonly toastService: ToastService,
        private readonly routeService: ActivatedRoute
    ) {
        this.cols = ["Codice", "Ordine", "Nome"];
        this.selectedTracks = [];
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    public save(): void {
        this.tenantsService.update(this.tenant.id, this.tenant).pipe(delay(1000), first()).subscribe({
            next: (tenant: Tenants) => {
                this.toastService.success("Successo", "Tenant aggiornato con successo");
                this.loadElement(tenant.id);
            }
        });
    }

    private loadElement(id: string) {
        this.tenantsService.getById(id).pipe(first()).subscribe({
            next: (tenant: Tenants) => {
                this.tenant = tenant;
            }
        });
    }
}
