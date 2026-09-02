import { DataViewLazyLoadEvent } from 'primeng/dataview';

/**
 * Guscio comune delle pagine elenco: stato di impaginazione, ordinamento,
 * ricerca e caricamento. La sola cosa che ogni pagina deve dichiarare è come
 * si recuperano i propri elementi.
 */
export abstract class ListPageBase {
    protected totalRecords = 0;
    protected loading = false;
    protected searchTerm = '';
    protected dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 12, sortField: 'name', sortOrder: 1 };

    protected abstract loadElements(search?: string): void;

    protected onLazyLoad(event: DataViewLazyLoadEvent): void {
        this.dataViewLazyLoadEvent = event;
        this.loadElements(this.searchTerm);
    }

    protected onSortValue(value: string): void {
        if (value.startsWith('!')) {
            this.dataViewLazyLoadEvent.sortOrder = -1;
            this.dataViewLazyLoadEvent.sortField = value.substring(1);
        } else {
            this.dataViewLazyLoadEvent.sortOrder = 1;
            this.dataViewLazyLoadEvent.sortField = value;
        }

        this.dataViewLazyLoadEvent.first = 0;
        this.loadElements(this.searchTerm);
    }

    protected onSearchChange(value: string): void {
        this.searchTerm = value;
        this.dataViewLazyLoadEvent.first = 0;
        this.loadElements(value);
    }

    protected get pageIndex(): number {
        return (this.dataViewLazyLoadEvent.first ?? 0) / (this.dataViewLazyLoadEvent.rows || 12);
    }

    protected get pageSize(): number {
        return this.dataViewLazyLoadEvent.rows || 12;
    }

    protected get sortCriteria(): string[] {
        return [`${this.dataViewLazyLoadEvent.sortField},${(this.dataViewLazyLoadEvent.sortOrder ?? 1) > 0 ? 'asc' : 'desc'}`];
    }
}
