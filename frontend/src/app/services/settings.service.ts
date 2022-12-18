import { Injectable } from "@angular/core";
import { Observable, Subject } from "rxjs";

@Injectable({
    providedIn: 'root'
})
export class SettingsService {
    private _languageChanged = new Subject<string>();
    private _languages: string[] = ['it', 'en'];
    private _isDarkTheme: boolean = window.matchMedia("(prefers-color-scheme: dark)").matches || false;

    public get languages(): string[] { return this._languages; }
    public get default(): string {
        this._languageChanged.next(this._languages[0]);
        return this._languages[0];
    }
    public set currentLang(lang: string) { this._languageChanged.next(lang); }
    public get isDarkTheme(): boolean { return this._isDarkTheme; }

    public languageChanged(): Observable<string> {
        return this._languageChanged.asObservable();
    }

    public toggleTheme(): boolean {
        this._isDarkTheme = !this._isDarkTheme;
        this.setDefalutTheme();
        return this._isDarkTheme;
    }

    public setDefalutTheme(): void {
        document.body.classList.toggle('darkTheme', this._isDarkTheme);
    }
}
