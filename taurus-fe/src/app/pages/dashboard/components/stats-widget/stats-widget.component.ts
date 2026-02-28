import { CommonModule } from "@angular/common";
import { ChangeDetectionStrategy, Component, Input } from "@angular/core";
import { RouterModule } from "@angular/router";

@Component({
    selector: 'app-stats-widget',
    imports: [
        CommonModule,
        RouterModule
    ],
    templateUrl: './stats-widget.component.html',
    styleUrl: './stats-widget.component.scss',
    host: {
        class: 'col-span-12 sm:col-span-6 md:col-span-4 xl:col-span-3',
    },
    changeDetection: ChangeDetectionStrategy.Default,
})
export class StatsWidgetComponent {
    @Input() label: string = "";
    @Input() value: number = 0;
    @Input() icon: string = "pi-shopping-cart";
    @Input() color: string = "blue";
    @Input() link: string = "";
}