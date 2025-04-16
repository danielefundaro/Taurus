import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { first } from 'rxjs';
import { AddInstrumentsDialogComponent } from '../../dialogs/add-instruments-dialog/add-instruments-dialog.component';
import { ImportsModule } from '../../imports';
import { Instruments, InstrumentsCriteria, Page } from '../../module';
import { InstrumentsService } from '../../service';
import { StringFilter } from '../../module/criteria/filter';

@Component({
    selector: 'app-instruments',
    imports: [
        RouterModule,
        ImportsModule,
    ],
    templateUrl: './instruments.component.html',
    styleUrl: './instruments.component.scss',
    providers: [
        InstrumentsService,
        DialogService
    ]
})
export class InstrumentsComponent {
    protected sortOptions!: SelectItem[];
    protected layout: 'list' | 'grid' = 'list';
    protected options = ['list', 'grid'];
    protected totalRecords: number = 0;
    protected dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 5, sortField: 'name.keyword', sortOrder: 1 };
    protected instruments: Instruments[];

    constructor(private readonly instrumentsService: InstrumentsService, private readonly dialogService: DialogService) {
        this.instruments = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Name A-Z', value: 'name.keyword' },
            { label: 'Name Z-A', value: '!name.keyword' },
        ];
    }

    protected onSortChange(event: SelectChangeEvent) {
        let value = event.value;

        if (value.indexOf('!') === 0) {
            this.dataViewLazyLoadEvent.sortOrder = -1;
            this.dataViewLazyLoadEvent.sortField = value.substring(1, value.length);
        } else {
            this.dataViewLazyLoadEvent.sortOrder = 1;
            this.dataViewLazyLoadEvent.sortField = value;
        }
    }

    protected onLazyLoad(event: DataViewLazyLoadEvent) {
        this.dataViewLazyLoadEvent = event;
        this.loadElements();
    }

    protected onGlobalFilter(event: Event): void {
        this.loadElements((event.target as HTMLInputElement).value);
    }

    protected initials(name?: string | null): string {
        return (name ?? '').split(" ").slice(0, 2).map(s => s[0]).join(" ").toUpperCase();
    }

    protected addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddInstrumentsDialogComponent, {
            header: "Aggiungi strumento",
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Instruments) => {
            if (result) {
                this.instrumentsService.create(result).pipe(first()).subscribe({
                    next: (album: Instruments) => {
                        this.loadElements();
                    }
                });
            }
        });
    }

    protected deleteElement(instrument: Instruments) {
        this.instrumentsService.delete(instrument.id).pipe(first()).subscribe({
            next: (value: any) => {
                this.loadElements();
            }
        });
    }

    private loadElements(search?: string) {
        const instrumentsCriteria: InstrumentsCriteria = new InstrumentsCriteria();
        instrumentsCriteria.page = this.dataViewLazyLoadEvent.first / this.dataViewLazyLoadEvent.rows;
        instrumentsCriteria.size = this.dataViewLazyLoadEvent.rows;
        instrumentsCriteria.sort = [`${this.dataViewLazyLoadEvent.sortField},${this.dataViewLazyLoadEvent.sortOrder > 0 ? "asc" : "desc"}`];

        if (search) {
            instrumentsCriteria.name = new StringFilter();
            instrumentsCriteria.name.contains = search;
        }

        this.instrumentsService.getAll(instrumentsCriteria).pipe(first()).subscribe({
            next: (value: Page<Instruments>) => {
                this.instruments = value.content;
                this.totalRecords = value.totalElements;
            }
        });
    }
}
