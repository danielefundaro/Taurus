import { CommonModule } from "@angular/common";
import { ChangeDetectionStrategy, Component, Input } from "@angular/core";
import { ButtonModule } from "primeng/button";
import { Chip } from "primeng/chip";
import { RippleModule } from "primeng/ripple";
import { TableModule } from "primeng/table";
import { ImportsModule } from "../../../../imports";
import { Tracks } from "../../../../module";

@Component({
    selector: 'app-recents-widget',
    imports: [
        CommonModule,
        TableModule,
        ButtonModule,
        RippleModule,
        ImportsModule,
        Chip,
    ],
    templateUrl: './recents-widget.component.html',
    styleUrl: './recents-widget.component.scss',
    host: {
        class: 'col-span-12 xl:col-span-6',
    },
    changeDetection: ChangeDetectionStrategy.Default,
})
export class RecentsWidgetComponent {
    @Input() tracks: Tracks[] = [];
}