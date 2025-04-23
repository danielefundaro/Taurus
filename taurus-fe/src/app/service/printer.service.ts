import { Injectable } from "@angular/core";
import { SheetsMusic } from "../module";

@Injectable({
    providedIn: 'root'
})
export class PrinterService {
    private _scores: SheetsMusic[] = [];

    constructor() {
        this._scores = [];
    }

    public get scores(): SheetsMusic[] {
        return this._scores;
    }

    public clear(): void {
        this._scores = [];
    }

    public push(...items: SheetsMusic[]): void {
        this._scores.push(...items);
    }
}