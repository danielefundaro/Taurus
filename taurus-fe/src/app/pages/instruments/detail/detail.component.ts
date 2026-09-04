import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { delay, finalize, first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import { ChildrenEntities, Instruments } from '../../../module';
import { ConfirmService, InstrumentsService, ToastService } from '../../../service';

@Component({
    selector: 'app-instrument-detail',
    imports: [ImportsModule],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [InstrumentsService]
})
export class DetailComponent extends DetailPageBase implements OnInit {
    public sortOptions!: SelectItem[];
    public totalRecords: number = 0;
    public instrument: Instruments = new Instruments();
    public cols: string[];
    public selectedTracks: ChildrenEntities[];

    constructor(
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
        private readonly routeService: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmService: ConfirmService
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
        this.confirmService.confirmDestructive({
            title: 'Elimina strumento',
            consequence: 'Lo strumento verrà eliminato definitivamente.',
            actionLabel: 'Elimina',
            accept: () => {
                this.instrumentsService
                    .delete(this.instrument.id)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.isDirty = false;
                            this.toastService.success('Successo', 'Strumento eliminato');
                            this.router.navigate(['/instruments']);
                        }
                    });
            }
        });
    }

    public save(): void {
        this.saving = true;
        this.instrumentsService
            .update(this.instrument.id, this.instrument)
            .pipe(
                delay(1000),
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (instrument: Instruments) => {
                    this.isDirty = false;
                    this.toastService.success('Successo', 'Strumento aggiornato con successo');
                    this.loadElement(instrument.id);
                }
            });
    }

    private loadElement(id: number | string) {
        this.instrumentsService
            .getById(Number(id))
            .pipe(first())
            .subscribe({
                next: (instrument: Instruments) => {
                    this.instrument = instrument;
                    this.isDirty = false;
                }
            });
    }
}
