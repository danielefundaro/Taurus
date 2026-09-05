import { CommonModule } from '@angular/common';
import { Component, effect, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { RoleEnums } from '../../constants';
import { HasRolesDirective } from '../../directive';
import { MenuItemComponent } from '../menu-item/menu-item.component';
import { TenantFeatureService } from '../../service';

@Component({
    selector: 'app-menu',
    imports: [CommonModule, MenuItemComponent, RouterModule, HasRolesDirective],
    templateUrl: './menu.component.html',
    styleUrl: './menu.component.scss'
})
export class MenuComponent implements OnInit {
    model: MenuItem[] = [];

    constructor(private readonly tenantFeatureService: TenantFeatureService) {
        effect(() => {
            this.tenantFeatureService.loaded();
            this.tenantFeatureService.financeEnabled();
            this.tenantFeatureService.inventoryEnabled();
            this.buildMenu();
        });
    }

    ngOnInit() {
        this.tenantFeatureService.refresh().subscribe({ error: () => undefined });
    }

    private buildMenu(): void {
        this.model = [
            {
                label: 'Home',
                hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.TREASURER, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL],
                items: [
                    {
                        label: 'Dashboard',
                        icon: 'pi pi-fw pi-home',
                        routerLink: ['/']
                    }
                ]
            },
            {
                label: 'Menu',
                icon: 'pi pi-fw pi-briefcase',
                routerLink: ['/pages'],
                hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.TREASURER, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL],
                items: [
                    {
                        label: 'Documenti legali',
                        icon: 'pi pi-fw pi-file-check',
                        routerLink: ['/legal-documents'],
                        hasRoles: [RoleEnums.SUPER_ADMIN]
                    },
                    {
                        label: 'Tenant',
                        icon: 'pi pi-fw pi-building',
                        routerLink: ['/tenants'],
                        hasRoles: [RoleEnums.SUPER_ADMIN]
                    },
                    {
                        label: 'Consegne notifiche',
                        icon: 'pi pi-fw pi-send',
                        routerLink: ['/admin/notification-delivery'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN]
                    },
                    {
                        label: 'Feed calendario',
                        icon: 'pi pi-fw pi-calendar-plus',
                        routerLink: ['/admin/calendar-feeds'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN]
                    },
                    {
                        label: 'Utenti',
                        icon: 'pi pi-fw pi-users',
                        routerLink: ['/users'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN]
                    },
                    {
                        label: 'Configurazione iniziale',
                        icon: 'pi pi-fw pi-file-import',
                        routerLink: ['/onboarding'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN]
                    },
                    {
                        label: 'Economia',
                        icon: 'pi pi-fw pi-wallet',
                        routerLink: ['/finance'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.TREASURER],
                        visible: this.tenantFeatureService.loaded() && this.tenantFeatureService.financeEnabled()
                    },
                    {
                        label: 'Inventario',
                        icon: 'pi pi-fw pi-box',
                        routerLink: ['/inventory'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL],
                        visible: this.tenantFeatureService.loaded() && this.tenantFeatureService.inventoryEnabled()
                    },
                    {
                        separator: true,
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL]
                    },
                    {
                        label: 'Album',
                        icon: 'pi pi-fw pi-book',
                        routerLink: ['/albums'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL]
                    },
                    {
                        label: 'Tracce',
                        icon: 'pi pi-fw pi-file',
                        routerLink: ['/tracks'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL]
                    },
                    {
                        label: 'Strumenti',
                        icon: 'pi pi-fw pi-sliders-h',
                        routerLink: ['/instruments'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST]
                    },
                    {
                        separator: true,
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL]
                    },
                    {
                        label: 'Calendario',
                        icon: 'pi pi-fw pi-calendar',
                        routerLink: ['/calendar'],
                        hasRoles: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL]
                    }
                ]
            }
        ];
    }
}
