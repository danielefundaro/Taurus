import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "src/environments/environment.prod";
import { Page, QueryPagination } from "../models";
import { CommonFields } from "../models/commonFields.model";

@Injectable({
    providedIn: 'root'
})
export abstract class CommonService<T extends CommonFields> {

    constructor(protected http: HttpClient) { }

    abstract baseApi(): string;
    abstract searches(filter?: string, index?: number, size?: number, sort?: string, direction?: string): Observable<Page<T>>;

    public getById(id: number): Observable<T> {
        return this.http.get<T>(`${environment.baseurl}/${this.baseApi()}/${id}`);
    }

    public getAll(): Observable<Array<T>> {
        return this.http.get<Array<T>>(`${environment.baseurl}/${this.baseApi()}/`);
    }

    public save(t: T): Observable<T> {
        return this.http.post<T>(`${environment.baseurl}/${this.baseApi()}/`, t);
    }

    public delete(id: number): Observable<T> {
        return this.http.delete<T>(`${environment.baseurl}/${this.baseApi()}/${id}`);
    }

    protected searchesQuery(queryPagination: QueryPagination): Observable<Page<T>> {
        return this.http.post<Page<T>>(`${environment.baseurl}/${this.baseApi()}/searches/`, queryPagination);
    }

    protected get getBaseUrl(): string { return `${environment.baseurl}/${this.baseApi()}`; }
}