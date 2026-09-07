import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { ListPageBase } from './list-page.base';

class TestListPage extends ListPageBase {
    readonly loads: Array<string | undefined> = [];

    initialize(key: string): void {
        this.initializeListState(key);
    }

    search(value: string): void {
        this.onSearchChange(value);
    }

    lazyLoad(event: DataViewLazyLoadEvent): void {
        this.onLazyLoad(event);
    }

    snapshot(): { search: string; event: DataViewLazyLoadEvent } {
        return { search: this.searchTerm, event: this.dataViewLazyLoadEvent };
    }

    protected loadElements(search?: string): void {
        this.loads.push(search);
    }
}

describe('ListPageBase', () => {
    it('restores search, pagination and sorting when returning to a list', () => {
        const firstVisit = new TestListPage();
        firstVisit.initialize('test-list-state');
        firstVisit.search('spartito');
        firstVisit.lazyLoad({ first: 24, rows: 12, sortField: 'name', sortOrder: -1 });

        const returnVisit = new TestListPage();
        returnVisit.initialize('test-list-state');

        expect(returnVisit.snapshot()).toEqual({
            search: 'spartito',
            event: { first: 24, rows: 12, sortField: 'name', sortOrder: -1 }
        });
    });
});
