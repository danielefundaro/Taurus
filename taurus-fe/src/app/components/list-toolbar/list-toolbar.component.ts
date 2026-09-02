import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { Subject, debounceTime, takeUntil } from 'rxjs';

export interface ListToolbarOption {
    label?: string;
    value?: any;
}

@Component({
    selector: 'app-list-toolbar',
    standalone: true,
    imports: [CommonModule, FormsModule, ButtonModule, IconFieldModule, InputIconModule, InputTextModule, SelectModule, SelectButtonModule],
    templateUrl: './list-toolbar.component.html',
    styleUrl: './list-toolbar.component.scss'
})
export class ListToolbarComponent implements OnDestroy {
    @Input() search = '';
    @Input() searchPlaceholder = 'Cerca…';
    @Input() sort?: string;
    @Input() sortOptions: ListToolbarOption[] = [];
    @Input() layout?: 'list' | 'grid';
    @Input() showSearch = true;
    @Input() showLayout = true;
    @Input() gridIcon = 'pi pi-th-large';
    @Output() searchChange = new EventEmitter<string>();
    @Output() sortChange = new EventEmitter<string>();
    @Output() layoutChange = new EventEmitter<'list' | 'grid'>();
    private readonly destroy$ = new Subject<void>();
    private readonly search$ = new Subject<string>();

    constructor() {
        this.search$.pipe(debounceTime(300), takeUntil(this.destroy$)).subscribe((value) => this.searchChange.emit(value));
    }

    updateSearch(value: string): void {
        this.search = value;
        this.search$.next(value);
    }
    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }
}
