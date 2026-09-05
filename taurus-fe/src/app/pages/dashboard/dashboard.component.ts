import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { catchError, finalize, first, forkJoin, of, Subscription } from 'rxjs';
import { RoleEnums } from '../../constants';
import { HasRolesDirective } from '../../directive';
import { CalendarEvents, CalendarEventsCriteria, InventoryAdminSummary, InventoryAssignmentSummary, InventoryUserSummary, Notices, NoticesCriteria, OperationalDashboard, Page, Tracks, TracksCriteria } from '../../module';
import { DateFilter } from '../../module/criteria/filter';
import {
    AlbumsService,
    CalendarEventsService,
    InventoryService,
    KeycloakService,
    LayoutService,
    NoticesService,
    NotificationCenterService,
    NotificationPreferencesService,
    OperationalDashboardService,
    TenantsService,
    TracksService,
    TenantFeatureService,
    ToastService,
    UserInventoryService,
    UsersService
} from '../../service';
import { CalendarEventsWidgetComponent } from './components/calendar-events-widget/calendar-events-widget.component';
import { InventoryWidgetComponent, InventoryWidgetMode } from './components/inventory-widget/inventory-widget.component';
import { NotificationsWidgetComponent } from './components/notifications-widget/notification-widget.component';
import { RecentsWidgetComponent } from './components/recents-widget/recents-widget.component';
import { StatsWidgetComponent } from './components/stats-widget/stats-widget.component';
import { OperationsWidgetComponent } from './components/operations-widget/operations-widget.component';

@Component({
    selector: 'app-dashboard',
    imports: [DatePipe, ConfirmDialogModule, NotificationsWidgetComponent, StatsWidgetComponent, RecentsWidgetComponent, CalendarEventsWidgetComponent, InventoryWidgetComponent, OperationsWidgetComponent, HasRolesDirective],
    templateUrl: './dashboard.component.html',
    styleUrl: './dashboard.component.scss',
    providers: [KeycloakService, TenantsService, UsersService, AlbumsService, TracksService, CalendarEventsService, InventoryService, UserInventoryService],
    changeDetection: ChangeDetectionStrategy.Default
})
export class DashboardComponent implements OnInit, OnDestroy {
    protected totalTenants: number = 0;
    protected totalUsers: number = 0;
    protected totalAlbums: number = 0;
    protected totalTracks: number = 0;
    protected tracks: Tracks[] = [];
    protected upcomingEvents?: Page<CalendarEvents>;
    protected notices?: Page<Notices>;
    protected noticeView: 'ACTIVE' | 'SNOOZED' = 'ACTIVE';
    protected inventoryMode: InventoryWidgetMode = 'user';
    protected inventoryAdminSummary?: InventoryAdminSummary;
    protected inventoryUserSummary?: InventoryUserSummary;
    protected recentInventoryAssignments: InventoryAssignmentSummary[] = [];
    protected inventoryLoading: boolean = true;
    protected readonly RoleEnums: typeof RoleEnums = RoleEnums;
    protected operations?: OperationalDashboard;
    protected operationsInitialLoading = true;
    protected operationsRefreshing = false;
    protected operationsError = false;
    protected operationsRefreshWarning = false;
    protected operationsAnnouncement = '';
    protected readonly inventoryEnabled;
    private operationsLoadedAt = 0;
    private $readSubscription?: Subscription;
    private $deleteSubscription?: Subscription;

    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly tenantsService: TenantsService,
        private readonly usersService: UsersService,
        private readonly albumsService: AlbumsService,
        private readonly tracksService: TracksService,
        private readonly calendarEventsService: CalendarEventsService,
        private readonly inventoryService: InventoryService,
        private readonly userInventoryService: UserInventoryService,
        private readonly noticesService: NoticesService,
        private readonly layoutService: LayoutService,
        private readonly notificationCenter: NotificationCenterService,
        private readonly notificationPreferencesService: NotificationPreferencesService,
        private readonly operationalDashboardService: OperationalDashboardService,
        private readonly tenantFeatureService: TenantFeatureService,
        private readonly toastService: ToastService,
        private readonly router: Router
    ) {
        this.inventoryEnabled = this.tenantFeatureService.inventoryEnabled;
    }

    ngOnInit(): void {
        this.tenantFeatureService
            .refresh()
            .pipe(
                first(),
                catchError(() => of(null))
            )
            .subscribe(() => this.initializeDashboard());
    }

    private initializeDashboard(): void {
        this.loadOperations();
        const role = this.keycloakService.currentUserRole;

        switch (role) {
            case RoleEnums.SUPER_ADMIN:
                this.tenantsService
                    .getAll()
                    .pipe(first())
                    .subscribe((tenants) => {
                        this.totalTenants = tenants.totalElements;
                    });
                this.adminMethods();
                break;
            case RoleEnums.ADMIN:
                this.adminMethods();
                break;
            case RoleEnums.TREASURER:
                this.loadNotices();
                break;
            case RoleEnums.ARCHIVIST:
            case RoleEnums.USER:
            case RoleEnums.USER_EXTERNAL:
                this.userMethods();
                break;
        }
    }

    @HostListener('document:visibilitychange')
    protected onVisibilityChange(): void {
        if (document.visibilityState === 'visible' && this.operationsLoadedAt > 0 && Date.now() - this.operationsLoadedAt >= 5 * 60 * 1000) {
            this.loadOperations();
        }
    }

    protected refreshOperations(): void {
        this.loadOperations(true);
    }

    ngOnDestroy(): void {
        if (this.$readSubscription) {
            this.$readSubscription.unsubscribe();
        }

        if (this.$deleteSubscription) {
            this.$deleteSubscription.unsubscribe();
        }
    }

    protected pageChange(event: { page: number; size: number }): void {
        this.loadNotices(event.page, event.size);
    }

    protected changeNoticeView(view: 'ACTIVE' | 'SNOOZED'): void {
        this.noticeView = view;
        this.loadNotices();
    }

    protected snoozeNotice(event: { notice: Notices; until: Date }): void {
        this.noticesService
            .snooze(event.notice.id, event.until)
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Promemoria impostato', `La notifica tornerà il ${event.until.toLocaleString('it-IT')}.`);
                this.notificationCenter.refresh();
                this.loadNotices();
            });
    }

    protected unsnoozeNotice(notice: Notices): void {
        this.noticesService
            .unsnooze(notice.id)
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Notifica ripristinata', 'La notifica è di nuovo tra quelle attive.');
                this.notificationCenter.refresh();
                this.loadNotices();
            });
    }

    /**
     * Opt-out rapido dal centro notifiche: disattiva il canale in-app della categoria
     * riscrivendo l'intero aggregato delle preferenze, come richiede la PUT del backend.
     * Vale solo per i nuovi eventi: le notifiche già presenti restano visibili.
     */
    protected disableNoticeCategory(notice: Notices): void {
        const source = notice.source;
        if (!source) return;
        this.notificationPreferencesService
            .getPreferences()
            .pipe(first())
            .subscribe((preferences) => {
                const category = preferences.categories.find((entry) => entry.source === source);
                if (!category || !category.inAppEnabled) {
                    this.toastService.success('Categoria già disattivata', 'Non riceverai nuove notifiche di questa categoria nel centro.');
                    return;
                }
                category.inAppEnabled = false;
                this.notificationPreferencesService
                    .savePreferences(preferences)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success(
                            'Categoria disattivata',
                            'I nuovi aggiornamenti di questa categoria non compariranno nel centro notifiche. Le notifiche già presenti restano visibili.'
                        );
                    });
            });
    }

    protected upcomingEventsPageChange(event: { page: number; size: number }): void {
        this.loadUpcomingEvents(event.page, event.size);
    }

    protected markNoticesAsRead(noticeIds: number[] | null): void {
        let noticesMarked = null;

        if (!noticeIds || noticeIds.length === 0) {
            noticesMarked = [this.noticesService.markAllAsRead()];
        } else {
            noticesMarked = noticeIds.map((id) => this.noticesService.markAsRead(id));
        }

        if (noticesMarked) {
            this.$readSubscription = forkJoin(noticesMarked).subscribe(() => {
                this.loadNotices();
            });
        }
    }

    protected navigateToNotice(notice: Notices): void {
        if (!notice.targetPath?.startsWith('/') || notice.targetPath.startsWith('//')) return;
        if (notice.readDate) {
            this.router.navigateByUrl(notice.targetPath);
            return;
        }
        this.$readSubscription = this.noticesService
            .markAsRead(notice.id)
            .pipe(first())
            .subscribe(() => {
                this.notificationCenter.refresh();
                this.router.navigateByUrl(notice.targetPath!);
            });
    }

    protected deleteNotices(noticeIds: number[] | null): void {
        let noticesDeleted = null;

        if (!noticeIds || noticeIds.length === 0) {
            noticesDeleted = [this.noticesService.deleteAll()];
        } else {
            noticesDeleted = noticeIds.map((id) => this.noticesService.delete(id));
        }

        if (noticesDeleted) {
            this.$deleteSubscription = forkJoin(noticesDeleted).subscribe(() => {
                this.loadNotices();
            });
        }
    }

    protected downloadInventoryReport(): void {
        this.userInventoryService
            .downloadReport(true, true, true)
            .pipe(first())
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                const anchor = document.createElement('a');
                anchor.href = url;
                anchor.download = 'prospetto-inventario.pdf';
                anchor.click();
                URL.revokeObjectURL(url);
            });
    }

    private adminMethods() {
        this.inventoryMode = 'admin';
        this.usersService
            .getAll()
            .pipe(first())
            .subscribe((users) => {
                this.totalUsers = users.totalElements;
            });
        this.loadCommonDashboardData();
        if (!this.inventoryEnabled()) {
            this.inventoryLoading = false;
            return;
        }
        this.inventoryService
            .getSummary()
            .pipe(first())
            .subscribe({
                next: (summary) => {
                    this.inventoryAdminSummary = summary;
                    this.inventoryLoading = false;
                },
                error: () => (this.inventoryLoading = false)
            });
    }

    private userMethods() {
        this.inventoryMode = 'user';
        this.loadCommonDashboardData();
        if (!this.inventoryEnabled()) {
            this.inventoryLoading = false;
            return;
        }
        forkJoin({
            summary: this.userInventoryService.getSummary(),
            assignments: this.userInventoryService.getAssignments('', 'POSSESSED', 0, 3, 'assignedAt,desc')
        })
            .pipe(first())
            .subscribe({
                next: (result) => {
                    this.inventoryUserSummary = result.summary;
                    this.recentInventoryAssignments = result.assignments.content;
                    this.inventoryLoading = false;
                },
                error: () => (this.inventoryLoading = false)
            });
    }

    private loadCommonDashboardData() {
        const tracksCriteria = new TracksCriteria();
        tracksCriteria.page = 0;
        tracksCriteria.size = 10;
        tracksCriteria.sort = ['insertDate,desc'];

        this.albumsService
            .getAll()
            .pipe(first())
            .subscribe((albums) => {
                this.totalAlbums = albums.totalElements;
            });
        this.tracksService
            .getAll(tracksCriteria)
            .pipe(first())
            .subscribe((tracks) => {
                this.totalTracks = tracks.totalElements;
                this.tracks = tracks.content;
            });
        this.loadUpcomingEvents();
        this.loadNotices();
    }

    private loadUpcomingEvents(page: number = 0, size: number = 10): void {
        const upcomingCriteria = new CalendarEventsCriteria();
        upcomingCriteria.page = page;
        upcomingCriteria.size = size;
        upcomingCriteria.sort = ['startDate,asc'];
        upcomingCriteria.startDate = new DateFilter();
        upcomingCriteria.startDate.greaterThanOrEqual = new Date();

        this.calendarEventsService
            .getAll(upcomingCriteria)
            .pipe(first())
            .subscribe((events) => {
                this.upcomingEvents = events;
            });
    }

    private loadNotices(page: number = 0, size: number = 10): void {
        const noticesCriteria = new NoticesCriteria();
        noticesCriteria.page = page;
        noticesCriteria.size = size;
        noticesCriteria.sort = ['insertDate,desc'];
        noticesCriteria.view = this.noticeView;

        this.noticesService
            .getAll(noticesCriteria)
            .pipe(first())
            .subscribe((notices) => {
                this.notices = notices;
            });

        this.noticesService
            .countUnread()
            .pipe(first())
            .subscribe((count) => {
                this.layoutService.notificationNumber.set(count);
            });
    }

    private loadOperations(manual = false): void {
        const hasSnapshot = !!this.operations;
        this.operationsInitialLoading = !hasSnapshot;
        this.operationsRefreshing = hasSnapshot;
        this.operationsError = false;
        this.operationsRefreshWarning = false;
        if (manual) this.operationsAnnouncement = '';
        this.operationalDashboardService
            .getOperations()
            .pipe(
                first(),
                finalize(() => {
                    this.operationsInitialLoading = false;
                    this.operationsRefreshing = false;
                })
            )
            .subscribe({
                next: (dashboard) => {
                    this.operations = dashboard;
                    this.operationsLoadedAt = Date.now();
                    if (manual) this.operationsAnnouncement = 'Attività aggiornate.';
                },
                error: () => {
                    if (hasSnapshot) this.operationsRefreshWarning = true;
                    else this.operationsError = true;
                    if (manual) this.operationsAnnouncement = 'Aggiornamento non riuscito.';
                }
            });
    }
}
