import { DataSource } from "@angular/cdk/collections";
import { EventEmitter } from "@angular/core";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSourcePaginator } from "@angular/material/table";
import { Observable, BehaviorSubject, merge, map, Subject } from "rxjs";
import { CommonFields } from "./commonFields.model";

export class PersonalDataSource<T extends CommonFields, P extends MatTableDataSourcePaginator = MatTableDataSourcePaginator> extends DataSource<T> {
    private _loadingSubject = new BehaviorSubject<boolean>(false);
    private _dataStream = new Subject<T[]>();
    private _data: Array<T> = new Array<T>();
    private _sort!: MatSort | undefined;
    private _paginator!: P | undefined;
    private _refresh: EventEmitter<any> = new EventEmitter<any>();

    public get refresh(): EventEmitter<any> { return this._refresh; }

    public get loading$(): Observable<boolean> { return this._loadingSubject.asObservable() };
    public get data(): T[] { return this._data; }
    public get paginator(): P | undefined { return this._paginator; }
    public get sort(): MatSort | undefined { return this._sort; }

    constructor(paginator?: P, sort?: MatSort) {
        super();
        this._data = [];
        this._paginator = paginator;
        this._sort = sort;
        this.setData(this._data);
    }

    public connect(): Observable<T[]> {
        if (this._sort && this._paginator) {
            return merge(this._sort.sortChange, this._paginator.page, this._dataStream, this._refresh).pipe(map(() => {
                return this.getPagedData(this.getSortedData(this._data));
            }));
        }

        if (this._sort) {
            return merge(this._sort.sortChange, this._dataStream, this._refresh).pipe(map(() => {
                return this.getPagedData(this.getSortedData(this._data));
            }));
        }

        if (this._paginator) {
            return merge(this._paginator.page, this._dataStream, this._refresh).pipe(map(() => {
                return this.getPagedData(this.getSortedData(this._data));
            }));
        }

        return merge(this._dataStream, this._refresh).pipe(map(() => this.getPagedData(this.getSortedData(this._data))));
    }

    public disconnect(): void {
        this._dataStream.complete();
        this._loadingSubject.complete();
    }

    public set(items: T[]): void {
        this._data = items;
        this.setData(this._data);
    }

    public push(...items: T[]): void {
        this._data.push(...items);
        this.setData(this._data);
    }

    public removeAt(index: number, deleteCount: number = 1): T[] {
        if (this._paginator) {
            index += this._paginator.pageIndex * this._paginator.pageSize;
        }

        const splice = this._data.splice(index, deleteCount);
        this.setData(this._data);
        return splice;
    }

    public clear(): void {
        this._data = [];
        this.setData(this._data);
    }

    private setData(data: T[]): void {
        this._loadingSubject.next(true);
        this._dataStream.next(data);

        if (this._paginator) {
            this._paginator.length = data.length;
        }

        this._loadingSubject.next(false);
    }

    private getPagedData(data: T[]) {
        if (this._paginator != null) {
            const startIndex = this._paginator.pageIndex * this._paginator.pageSize;
            return data.slice(startIndex, startIndex + this._paginator.pageSize);
        }

        return data;
    }

    private getSortedData(data: T[]) {
        if (!this._sort?.active || this._sort?.direction === "") {
            return data;
        }

        return data.slice().sort((a, b) => {
            const isAsc = this._sort?.direction === 'asc';
            if (this._sort?.active) {
                const key = this._sort?.active as keyof T;
                const aKeys = Object.keys(a).includes(key.toString());
                const bKeys = Object.keys(b).includes(key.toString());

                if (aKeys && bKeys) {
                    return (a[key] <= b[key] ? -1 : 1) * (isAsc ? 1 : -1);
                }
            }

            return 0;
        });
    }
}