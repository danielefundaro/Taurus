import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { first } from 'rxjs';
import { RoleEnums } from '../../constants';
import { HasRolesDirective } from '../../directive';
import { Tracks, TracksCriteria } from '../../module';
import { AlbumsService, KeycloakService, TenantsService, TracksService, UsersService } from '../../service';
import { NotificationsWidget } from './components/notificationswidget';
import { RecentsWidgetComponent } from './components/recents-widget/recents-widget.component';
import { StatsWidgetComponent } from './components/stats-widget/stats-widget.component';

@Component({
    selector: 'app-dashboard',
    imports: [
        NotificationsWidget,
        StatsWidgetComponent,
        RecentsWidgetComponent,
        HasRolesDirective,
    ],
    templateUrl: './dashboard.component.html',
    styleUrl: './dashboard.component.scss',
    providers: [
        KeycloakService,
        TenantsService,
        UsersService,
        AlbumsService,
        TracksService,
    ],
    changeDetection: ChangeDetectionStrategy.Default,
})
export class DashboardComponent implements OnInit {
    protected totalTenants: number = 0;
    protected totalUsers: number = 0;
    protected totalAlbums: number = 0;
    protected totalTracks: number = 0;
    protected tracks: Tracks[] = [];
    protected readonly RoleEnums: typeof RoleEnums = RoleEnums;

    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly tenantsService: TenantsService,
        private readonly usersService: UsersService,
        private readonly albumsService: AlbumsService,
        private readonly tracksService: TracksService,
    ) {
    }

    ngOnInit(): void {
        const role = this.keycloakService.currentUserRole;
        const criteria = new TracksCriteria();
        criteria.page = 0;
        criteria.size = 10;
        criteria.sort = ['insertDate,desc'];

        switch (role) {
            case RoleEnums.SUPER_ADMIN:
                this.tenantsService.getAll().pipe(first()).subscribe(tenants => {
                    this.totalTenants = tenants.totalElements;
                });
                this.adminMethods(criteria);
                break;
            case RoleEnums.ADMIN:
                this.adminMethods(criteria);
                break;
            case RoleEnums.ARCHIVIST:
            case RoleEnums.USER:
                this.userMethods(criteria);
                break;
        }
    }

    private adminMethods(criteria: TracksCriteria) {
        this.usersService.getAll().pipe(first()).subscribe(users => {
            this.totalUsers = users.totalElements;
        });
        this.userMethods(criteria);
    }

    private userMethods(criteria: TracksCriteria) {
        this.albumsService.getAll().pipe(first()).subscribe(albums => {
            this.totalAlbums = albums.totalElements;
        });
        this.tracksService.getAll(criteria).pipe(first()).subscribe(tracks => {
            this.totalTracks = tracks.totalElements;
            this.tracks = tracks.content;
        });
    }
}
