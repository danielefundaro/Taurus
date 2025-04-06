import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CommonFieldsOpenSearch, CommonOpenSearchCriteria, Page } from '../module';

@Injectable({
    providedIn: 'root'
})
export abstract class CommonOpenSearchService<D extends CommonFieldsOpenSearch, C extends CommonOpenSearchCriteria> {

    private readonly _baseUrl: string;
    get baseUrl(): string {
        return this._baseUrl;
    }

    constructor(protected http: HttpClient) {
        this._baseUrl = environment.baseUrl;
    }

    abstract resourceName(): string;

    public create(d: D): Observable<D> {
        return this.http.post<D>(`${this._baseUrl}/${this.resourceName()}`, d);
    }

    public update(id: string, d: D): Observable<D> {
        return this.http.put<D>(`${this._baseUrl}/${this.resourceName()}/${id}`, d);
    }

    public partialUpdate(id: string, d: D): Observable<D> {
        return this.http.patch<D>(`${this._baseUrl}/${this.resourceName()}/${id}`, d);
    }

    public getAll(c?: C): Observable<Page<D>> {
        const options = this.createRequestOption(c);
        return this.http.get<Page<D>>(`${this._baseUrl}/${this.resourceName()}`, { params: options, observe: 'body' });
    }

    public getById(id: string): Observable<D> {
        return this.http.get<D>(`${this._baseUrl}/${this.resourceName()}/${id}`);
    }

    public delete(id: string): Observable<void> {
        return this.http.delete<void>(`${this._baseUrl}/${this.resourceName()}/${id}`);
    }

    private createRequestOption(req?: any): HttpParams {
        let options: HttpParams = new HttpParams();

        if (req) {
            Object.entries(req).forEach(([key, val]) => {
                if (val !== undefined && val !== null) {
                    for (const value of [].concat((req[key])).filter(v => v !== '')) {
                        options = options.append(key, value);
                    }
                }
            });
        }

        return options;
    };
}
