import { Injectable } from "@angular/core";

@Injectable({
    providedIn: 'root'
})
export class PrinterService {
    private _mediaStreams: string[] = [];

    constructor() {
        this._mediaStreams = [];
    }

    public get mediaStream(): string[] {
        return this._mediaStreams;
    }

    public clear(): void {
        this._mediaStreams = [];
    }

    public push(...items: string[]): void {
        this._mediaStreams.push(...items);
    }
}