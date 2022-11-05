import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';
import { Media } from '../models';

@Injectable({
    providedIn: 'root'
})
export class PrinterService {
    private _media: Media[] = [];
    private _dataStream = new BehaviorSubject<Media[]>([]);

    public get media(): Media[] { return this._media; }

    constructor(private router: Router) { }

    public clear(): void {
        this._media = [];
        this._dataStream.complete();
    }

    public connect(): Observable<Media[]> {
        return this._dataStream.asObservable();
    }

    public preview(): void {
        this.router.navigate(['preview']);
    }

    public push(...items: Media[]) {
        this._media.push(...items);
        this._dataStream.next(this._media);
    }

    public remove(index: number): Media[] {
        const media = this._media.splice(index, 1);
        this._dataStream.next(this._media);

        return media;
    }
}
