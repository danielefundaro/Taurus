import { Injectable } from "@angular/core";
import { BehaviorSubject, Observable } from "rxjs";

@Injectable({
    providedIn: 'root'
})
export class LoadingService {
    private readonly _loading: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
    public get loading(): Observable<boolean> {
        return this._loading.asObservable();
    }
    public set loading(value: boolean) {
        this._loading.next(value);
    }
}