import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { first } from 'rxjs';
import { RoleEnums } from '../../constants';
import { AlbumsService, KeycloakService, MediaService, TenantsService, TracksService, UsersService } from '../../service';
import { BestSellingWidget } from './components/bestsellingwidget';
import { NotificationsWidget } from './components/notificationswidget';
import { RecentSalesWidget } from './components/recentsaleswidget';
import { StatsWidgetComponent } from './components/stats-widget/stats-widget.component';
import { HasRolesDirective } from 'keycloak-angular';

@Component({
    selector: 'app-dashboard',
    imports: [
        RecentSalesWidget,
        BestSellingWidget,
        NotificationsWidget,
        StatsWidgetComponent,
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
        MediaService,
    ],
    changeDetection: ChangeDetectionStrategy.Default,
})
export class DashboardComponent implements OnInit {
    protected totalTenants: number = 0;
    protected totalUsers: number = 0;
    protected totalAlbums: number = 0;
    protected totalTracks: number = 0;
    protected totalMedia: number = 0;
    protected readonly RoleEnums: typeof RoleEnums = RoleEnums;

    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly tenantsService: TenantsService,
        private readonly usersService: UsersService,
        private readonly albumsService: AlbumsService,
        private readonly tracksService: TracksService,
        private readonly mediaService: MediaService,
    ) {
    }

    ngOnInit(): void {
        this.keycloakService.currentUserRoles.some(role => {
            switch (role) {
                case RoleEnums.SUPER_ADMIN:
                    this.tenantsService.getAll().pipe(first()).subscribe(tenants => {
                        this.totalTenants = tenants.totalElements;
                    });
                    break;
                case RoleEnums.ADMIN:
                    this.usersService.getAll().pipe(first()).subscribe(users => {
                        this.totalUsers = users.totalElements;
                    });
                    break;
                case RoleEnums.ARCHIVIST:
                    this.mediaService.getAll().pipe(first()).subscribe(media => {
                        this.totalMedia = media.totalElements;
                    });
                    break;
                case RoleEnums.USER:
                    this.albumsService.getAll().pipe(first()).subscribe(albums => {
                        this.totalAlbums = albums.totalElements;
                    });
                    this.tracksService.getAll().pipe(first()).subscribe(tracks => {
                        this.totalTracks = tracks.totalElements;
                    });
                    break;
            }
        });
    }
}
