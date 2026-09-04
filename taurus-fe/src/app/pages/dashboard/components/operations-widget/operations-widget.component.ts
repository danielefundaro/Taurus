import { ChangeDetectionStrategy, Component, EventEmitter, Input, isDevMode, Output } from '@angular/core';
import { Router } from '@angular/router';
import { ImportsModule } from '../../../../imports';
import { DashboardSeverity, OperationalDashboard, OperationalItem } from '../../../../module/operational-dashboard';

@Component({
    selector: 'app-operations-widget',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './operations-widget.component.html',
    styleUrl: './operations-widget.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class OperationsWidgetComponent {
    @Input() dashboard?: OperationalDashboard;
    @Input() initialLoading = false;
    @Input() refreshing = false;
    @Input() error = false;
    @Input() refreshWarning = false;
    @Input() announcement = '';
    @Output() refreshRequested = new EventEmitter<void>();

    private readonly allowedTargets: Readonly<Record<OperationalItem['type'], readonly string[]>> = {
        LEGAL_ACCEPTANCE_REQUIRED: ['/legal/accept'],
        CALENDAR_AVAILABILITY_REQUIRED: ['/calendar?attention=my-missing-availability'],
        CALENDAR_RESPONSES_MISSING: ['/calendar?attention=missing-availability'],
        INVENTORY_DECISION_REQUIRED: ['/inventory?view=mine&attention=pending-decisions'],
        INVENTORY_DECISIONS_PENDING: ['/inventory?attention=pending-decisions'],
        INVENTORY_RETURNS_PENDING: ['/inventory?attention=pending-returns'],
        INVENTORY_ASSIGNMENTS_EXPIRING: ['/inventory?attention=expiring', '/inventory?view=mine&attention=expiring'],
        FINANCE_MOVEMENTS_UNRECONCILED: ['/finance?section=movements&reconciled=false'],
        NOTIFICATION_DELIVERY_FAILED: ['/admin/notification-delivery?status=FAILED']
    };

    constructor(private readonly router: Router) {}

    protected severity(item: OperationalItem): 'danger' | 'warn' | 'info' {
        return item.severity === 'WARNING' ? 'warn' : (item.severity.toLowerCase() as 'danger' | 'info');
    }

    protected severityLabel(severity: DashboardSeverity): string {
        switch (severity) {
            case 'DANGER':
                return 'Scaduto o bloccante';
            case 'WARNING':
                return 'Da gestire';
            case 'INFO':
                return 'Da verificare';
        }
    }

    protected navigate(item: OperationalItem): void {
        const targets = this.allowedTargets[item.type];
        if (!item.targetPath.startsWith('/') || item.targetPath.startsWith('//') || !targets.includes(item.targetPath)) {
            if (isDevMode()) console.warn('Collegamento operativo non valido', item.type);
            return;
        }
        void this.router.navigateByUrl(item.targetPath);
    }
}
