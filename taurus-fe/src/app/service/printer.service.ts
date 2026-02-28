import { inject, Injectable } from "@angular/core";
import { Router } from "@angular/router";
import { forkJoin, Subscription } from "rxjs";
import { TracksService } from ".";
import { Albums, ChildrenEntities, SheetsMusic, Tracks } from "../module";

@Injectable({
    providedIn: 'root'
})
export class PrinterService {
    private _scores: SheetsMusic[] = [];
    private readonly tracksService = inject(TracksService);
    private readonly router = inject(Router);
    private $subscription?: Subscription;

    constructor() {
        this._scores = [];
    }

    public get scores(): SheetsMusic[] {
        return this._scores;
    }

    public preview(element: Albums | Tracks, selectedTracks?: ChildrenEntities[]): void {
        if (typeof element === "object" && "scores" in element) {
            this._scores.push(...element.scores!);
            this.router.navigate(["preview"]);
        } else if (typeof element === "object" && "tracks" in element) {
            let childrenEntities: ChildrenEntities[] = [];

            if (selectedTracks && selectedTracks.length > 0) {
                childrenEntities = selectedTracks;
            } else if (element.tracks && element.tracks.length > 0) {
                childrenEntities = element.tracks;
            }

            this.$subscription = forkJoin(childrenEntities.map(track => this.tracksService.getById(track.index))).subscribe(tracks => {
                this._scores.push(...tracks.flatMap(track => track.scores!))
                this.router.navigate(["preview"]);
            });
        }
    }

    public clear(): void {
        this._scores = [];

        if (this.$subscription) {
            this.$subscription.unsubscribe();
        }
    }
}