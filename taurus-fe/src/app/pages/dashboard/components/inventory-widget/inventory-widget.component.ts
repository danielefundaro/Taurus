import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { InventoryAdminSummary, InventoryAssignmentSummary, InventoryDecisionType, InventoryUserSummary } from '../../../../module';

export type InventoryWidgetMode = 'admin' | 'user';

@Component({
    selector: 'app-inventory-widget',
    imports: [ButtonModule, DatePipe, RouterModule, TagModule],
    templateUrl: './inventory-widget.component.html',
    styleUrl: './inventory-widget.component.scss',
    host: {
        class: 'col-span-12'
    },
    changeDetection: ChangeDetectionStrategy.Default
})
export class InventoryWidgetComponent {
    @Input({ required: true }) mode: InventoryWidgetMode = 'user';
    @Input() adminSummary?: InventoryAdminSummary;
    @Input() userSummary?: InventoryUserSummary;
    @Input() recentAssignments: InventoryAssignmentSummary[] = [];
    @Input() loading: boolean = false;
    @Output() reportDownload: EventEmitter<void> = new EventEmitter<void>();

    protected get isAdmin(): boolean {
        return this.mode === 'admin';
    }

    protected get availabilityPercentage(): number {
        const total = this.adminSummary?.totalQuantity ?? 0;
        if (total === 0) return 0;
        return Math.round(((this.adminSummary?.availableQuantity ?? 0) / total) * 100);
    }

    protected get adminActivities(): number {
        return (this.adminSummary?.pendingDecisions ?? 0) + (this.adminSummary?.pendingReturns ?? 0);
    }

    protected decisionLabel(decision?: InventoryDecisionType): string {
        if (!decision) return 'Da confermare';
        return decision === 'ACCEPTED' ? 'Accettato' : 'Rifiutato';
    }

    protected decisionSeverity(decision?: InventoryDecisionType): 'warn' | 'success' | 'danger' {
        if (!decision) return 'warn';
        return decision === 'ACCEPTED' ? 'success' : 'danger';
    }

    protected downloadReport(): void {
        this.reportDownload.emit();
    }
}
