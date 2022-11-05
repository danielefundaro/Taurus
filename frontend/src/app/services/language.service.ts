import { Injectable } from "@angular/core";
import { Observable, Subject } from "rxjs";

@Injectable({
    providedIn: 'root'
})
export class LanguageService {
    private _languageChanged = new Subject<string>();
    private _languages: string[] = ['it', 'en'];

    public get languages(): string[] { return this._languages; }
    public get default(): string {
        this._languageChanged.next(this._languages[0]);
        return this._languages[0];
    }
    public set current(lang: string) { this._languageChanged.next(lang); }

    public languageChanged(): Observable<string> {
        return this._languageChanged.asObservable();
    }
}
