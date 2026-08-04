import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ConfirmationService, SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { delay, first, forkJoin } from 'rxjs';
import { AddInstrumentsDialogComponent } from '../../dialogs/add-instruments-dialog/add-instruments-dialog.component';
import { ImportsModule } from '../../imports';
import { Instruments, InstrumentsCriteria, Page } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { InstrumentsService, ToastService } from '../../service';

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
        DialogService,
        ConfirmationService,
    ]
})
export class InstrumentsComponent implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: 'list' | 'grid' = 'list';
    protected options = ['list', 'grid'];
    protected totalRecords: number = 0;
    protected dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 10, sortField: 'name.keyword', sortOrder: 1 };
    protected instruments: Instruments[];
    protected selectedInstruments: Instruments[] = [];

    constructor(
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly confirmationService: ConfirmationService,
    ) {
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
                this.instrumentsService.create(result).pipe(delay(1000), first()).subscribe({
                    next: (instrument: Instruments) => {
                        this.toastService.success("Successo", "Strumento aggiunto con successo");
                        this.loadElements();
                    }
                });
            }
        });
    }

    protected deleteElement(instrument: Instruments): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Eliminare definitivamente questo strumento?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.instrumentsService.delete(instrument.id).pipe(delay(1000), first()).subscribe({
                    next: (value: any) => {
                        this.toastService.success("Successo", "Strumento eliminato con successo");
                        this.loadElements();
                    }
                });
            },
        });
    }

    protected isSelected(item: Instruments): boolean {
        return this.selectedInstruments.some(s => s.id === item.id);
    }

    protected isAllSelected(items: Instruments[]): boolean {
        return items.length > 0 && items.every(item => this.isSelected(item));
    }

    protected toggleSelection(item: Instruments): void {
        if (this.isSelected(item)) {
            this.selectedInstruments = this.selectedInstruments.filter(s => s.id !== item.id);
        } else {
            this.selectedInstruments = [...this.selectedInstruments, item];
        }
    }

    protected toggleSelectAll(items: Instruments[]): void {
        if (this.isAllSelected(items)) {
            this.selectedInstruments = this.selectedInstruments.filter(s => !items.some(item => item.id === s.id));
        } else {
            const notYetSelected = items.filter(item => !this.isSelected(item));
            this.selectedInstruments = [...this.selectedInstruments, ...notYetSelected];
        }
    }

    protected deleteSelectedElements(): void {
        const count = this.selectedInstruments.length;
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: `Eliminare definitivamente i ${count} strumenti selezionati?`,
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                forkJoin(this.selectedInstruments.map(item => this.instrumentsService.delete(item.id))).pipe(delay(1000), first()).subscribe({
                    next: () => {
                        this.selectedInstruments = [];
                        this.toastService.success('Successo', `${count} strumenti eliminati con successo`);
                        this.loadElements();
                    }
                });
            },
        });
    }

    private loadElements(search?: string) {
        this.selectedInstruments = [];
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
