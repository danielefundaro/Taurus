import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { delay, finalize, first, forkJoin } from 'rxjs';
import { AddInstrumentsDialogComponent } from '../../dialogs/add-instruments-dialog/add-instruments-dialog.component';
import { ImportsModule } from '../../imports';
import { Instruments, InstrumentsCriteria, Page } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { ConfirmService, InstrumentsService, ListLayout, ListLayoutService, ToastService } from '../../service';
import { ListPageBase } from '../_shared/list-page.base';

@Component({
    selector: 'app-instruments',
    imports: [RouterModule, ImportsModule],
    templateUrl: './instruments.component.html',
    styleUrl: './instruments.component.scss',
    providers: [InstrumentsService, DialogService]
})
export class InstrumentsComponent extends ListPageBase implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: ListLayout = 'list';
    protected instruments: Instruments[];
    protected selectedInstruments: Instruments[] = [];

    constructor(
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly confirmService: ConfirmService,
        private readonly listLayoutService: ListLayoutService
    ) {
        super();
        this.instruments = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Nome A-Z', value: 'name' },
            { label: 'Nome Z-A', value: '!name' }
        ];
        this.listLayoutService.observe('instruments', (value) => (this.layout = value));
    }

    protected onLayoutChange(value: ListLayout): void {
        this.layout = value;
        this.listLayoutService.set('instruments', value);
    }

    protected addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddInstrumentsDialogComponent, {
            header: 'Aggiungi strumento',
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '40rem',
            breakpoints: { '767px': 'calc(100vw - 2rem)' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Instruments) => {
            if (result) {
                this.instrumentsService
                    .create(result)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (instrument: Instruments) => {
                            this.toastService.success('Strumento aggiunto', 'Il nuovo strumento è ora disponibile nell’elenco.');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected deleteElement(instrument: Instruments): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina strumento',
            consequence: 'Lo strumento verrà eliminato definitivamente.',
            actionLabel: 'Elimina',
            accept: () => {
                this.instrumentsService
                    .delete(instrument.id)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (value: any) => {
                            this.toastService.success('Strumento eliminato', 'Lo strumento non è più visibile nell’elenco.');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected isSelected(item: Instruments): boolean {
        return this.selectedInstruments.some((s) => s.id === item.id);
    }

    protected isAllSelected(items: Instruments[]): boolean {
        return items.length > 0 && items.every((item) => this.isSelected(item));
    }

    protected toggleSelection(item: Instruments): void {
        if (this.isSelected(item)) {
            this.selectedInstruments = this.selectedInstruments.filter((s) => s.id !== item.id);
        } else {
            this.selectedInstruments = [...this.selectedInstruments, item];
        }
    }

    protected toggleSelectAll(items: Instruments[]): void {
        if (this.isAllSelected(items)) {
            this.selectedInstruments = this.selectedInstruments.filter((s) => !items.some((item) => item.id === s.id));
        } else {
            const notYetSelected = items.filter((item) => !this.isSelected(item));
            this.selectedInstruments = [...this.selectedInstruments, ...notYetSelected];
        }
    }

    protected deleteSelectedElements(): void {
        const count = this.selectedInstruments.length;
        this.confirmService.confirmDestructive({
            title: 'Elimina strumenti selezionati',
            consequence: `I ${count} strumenti selezionati verranno eliminati definitivamente.`,
            actionLabel: 'Elimina',
            accept: () => {
                forkJoin(this.selectedInstruments.map((item) => this.instrumentsService.delete(item.id)))
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: () => {
                            this.selectedInstruments = [];
                            this.toastService.success('Strumenti eliminati', `${count} strumenti non sono più visibili nell’elenco.`);
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected loadElements(search?: string) {
        this.selectedInstruments = [];
        this.loading = true;
        const instrumentsCriteria: InstrumentsCriteria = new InstrumentsCriteria();
        instrumentsCriteria.page = this.pageIndex;
        instrumentsCriteria.size = this.pageSize;
        instrumentsCriteria.sort = this.sortCriteria;

        if (search) {
            instrumentsCriteria.name = new StringFilter();
            instrumentsCriteria.name.contains = search;
        }

        this.instrumentsService
            .getAll(instrumentsCriteria)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (value: Page<Instruments>) => {
                    this.instruments = value.content;
                    this.totalRecords = value.totalElements;
                }
            });
    }
}
