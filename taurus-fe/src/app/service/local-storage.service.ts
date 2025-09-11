import { Injectable } from "@angular/core";
import { Preferences } from "../module";

@Injectable({
    providedIn: 'root'
})
export class LocalStorageService {
    constructor() {}

    public setItem(key: string, value: Preferences): void {
        localStorage.setItem(key, JSON.stringify(value));
    }

    public getItem(key: string): Preferences | null {
        const result: Preferences = JSON.parse(localStorage.getItem(key) ?? "{}");
        return Object.keys(result).length > 0 ? result : null;
    }

    public clear(): void {
        localStorage.clear();
    }
}