import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, Instruments } from '../../../module';
import { InstrumentsService } from '../../../service';

@Component({
    selector: 'app-instrument-detail',
    imports: [
        ImportsModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        InstrumentsService
    ],
})
export class DetailComponent {
    public sortOptions!: SelectItem[];
    public totalRecords: number = 0;
    public instrument: Instruments = new Instruments();
    public cols: string[];
    public selectedTracks: ChildrenEntities[];

    constructor(private readonly instrumentsService: InstrumentsService, private readonly routeService: ActivatedRoute) {
        this.cols = ["Codice", "Ordine", "Nome"];
        this.selectedTracks = [];
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    public save(): void {
        this.instrumentsService.update(this.instrument.id, this.instrument).pipe(first()).subscribe({
            next: (instrument: Instruments) => {
                this.loadElement(instrument.id);
            }
        });
    }

    private loadElement(id: string) {
        this.instrumentsService.getById(id).pipe(first()).subscribe({
            next: (instrument: Instruments) => {
                this.instrument = instrument;
            }
        });
    }
}
