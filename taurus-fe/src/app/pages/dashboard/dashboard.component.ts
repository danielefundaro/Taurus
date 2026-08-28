import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { first, forkJoin, Subscription } from 'rxjs';
import { RoleEnums } from '../../constants';
import { HasRolesDirective } from '../../directive';
import { CalendarEvents, CalendarEventsCriteria, InventoryAdminSummary, InventoryAssignmentSummary, InventoryUserSummary, Notices, NoticesCriteria, Page, Tracks, TracksCriteria } from '../../module';
import { DateFilter } from '../../module/criteria/filter';
import { AlbumsService, CalendarEventsService, InventoryService, KeycloakService, LayoutService, NoticesService, TenantsService, TracksService, UserInventoryService, UsersService } from '../../service';
import { CalendarEventsWidgetComponent } from './components/calendar-events-widget/calendar-events-widget.component';
import { InventoryWidgetComponent, InventoryWidgetMode } from './components/inventory-widget/inventory-widget.component';
import { NotificationsWidgetComponent } from './components/notifications-widget/notification-widget.component';
import { RecentsWidgetComponent } from './components/recents-widget/recents-widget.component';
import { StatsWidgetComponent } from './components/stats-widget/stats-widget.component';

@Component({
    selector: 'app-dashboard',
    imports: [NotificationsWidgetComponent, StatsWidgetComponent, RecentsWidgetComponent, CalendarEventsWidgetComponent, InventoryWidgetComponent, HasRolesDirective],
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
    protected upcomingEvents: CalendarEvents[] = [];
    protected notices?: Page<Notices>;
    protected inventoryMode: InventoryWidgetMode = 'user';
    protected inventoryAdminSummary?: InventoryAdminSummary;
    protected inventoryUserSummary?: InventoryUserSummary;
    protected recentInventoryAssignments: InventoryAssignmentSummary[] = [];
    protected inventoryLoading: boolean = true;
    protected readonly RoleEnums: typeof RoleEnums = RoleEnums;
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
        private readonly layoutService: LayoutService
    ) {}

    ngOnInit(): void {
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
            case RoleEnums.ARCHIVIST:
            case RoleEnums.USER:
            case RoleEnums.USER_EXTERNAL:
                this.userMethods();
                break;
        }
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

        const upcomingCriteria = new CalendarEventsCriteria();
        upcomingCriteria.page = 0;
        upcomingCriteria.size = 10;
        upcomingCriteria.sort = ['startDate,asc'];
        upcomingCriteria.startDate = new DateFilter();
        upcomingCriteria.startDate.greaterThanOrEqual = new Date();

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
        this.calendarEventsService
            .getAll(upcomingCriteria)
            .pipe(first())
            .subscribe((events) => {
                this.upcomingEvents = events.content;
            });
        this.loadNotices();
    }

    private loadNotices(page: number = 0, size: number = 10): void {
        const noticesCriteria = new NoticesCriteria();
        noticesCriteria.page = page;
        noticesCriteria.size = size;
        noticesCriteria.sort = ['insertDate,desc'];

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
}
