import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, SelectItem } from 'primeng/api';
import { delay, first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, Tenants } from '../../../module';
import { InstrumentsService, ToastService } from '../../../service';

@Component({
    selector: 'app-instrument-detail',
    imports: [
        ImportsModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        InstrumentsService,
        ConfirmationService,
    ],
})
export class DetailComponent implements OnInit {
    public sortOptions!: SelectItem[];
    public totalRecords: number = 0;
    public instrument: Tenants = new Tenants();
    public cols: string[];
    public selectedTracks: ChildrenEntities[];

    constructor(
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
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
            message: 'Eliminare definitivamente questo strumento?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.instrumentsService.delete(this.instrument.id).pipe(first()).subscribe({
                    next: () => {
                        this.toastService.success('Successo', 'Strumento eliminato');
                        this.router.navigate(['/instruments']);
                    },
                });
            },
        });
    }

    public save(): void {
        this.instrumentsService.update(this.instrument.id, this.instrument).pipe(delay(1000), first()).subscribe({
            next: (instrument: Tenants) => {
                this.toastService.success("Successo", "Strumento aggiornato con successo");
                this.loadElement(instrument.id);
            }
        });
    }

    private loadElement(id: string) {
        this.instrumentsService.getById(id).pipe(first()).subscribe({
            next: (instrument: Tenants) => {
                this.instrument = instrument;
            }
        });
    }
}
