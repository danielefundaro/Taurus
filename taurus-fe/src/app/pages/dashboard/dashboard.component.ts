import { ChangeDetectionStrategy, Component } from '@angular/core';
import { BestSellingWidget } from './components/bestsellingwidget';
import { NotificationsWidget } from './components/notificationswidget';
import { RecentSalesWidget } from './components/recentsaleswidget';
import { RevenueStreamWidget } from './components/revenuestreamwidget';
import { StatsWidget } from './components/statswidget';

@Component({
    selector: 'app-dashboard',
    imports: [
        StatsWidget,
        RecentSalesWidget,
        BestSellingWidget,
        RevenueStreamWidget,
        NotificationsWidget,
    ],
    templateUrl: './dashboard.component.html',
    styleUrl: './dashboard.component.scss',
    providers: [
    ],
    changeDetection: ChangeDetectionStrategy.Default,
})
export class DashboardComponent {}
