import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HasRolesDirective } from 'keycloak-angular';
import { MenuItem } from 'primeng/api';
import { MenuItemComponent } from '../menu-item/menu-item.component';

@Component({
    selector: 'app-menu',
    imports: [
        CommonModule,
        MenuItemComponent,
        RouterModule,
        HasRolesDirective,
    ],
    templateUrl: './menu.component.html',
    styleUrl: './menu.component.scss',
})
export class MenuComponent implements OnInit {
    model: MenuItem[] = [];

    ngOnInit() {
        this.model = [
            {
                label: 'Home',
                'hasRoles': ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'],
                items: [{
                    label: 'Dashboard',
                    icon: 'pi pi-fw pi-home',
                    routerLink: ['/']
                }]
            },
            {
                label: 'Pages',
                icon: 'pi pi-fw pi-briefcase',
                routerLink: ['/pages'],
                items: [
                    {
                        label: 'Tenants',
                        icon: 'pi pi-fw pi-globe',
                        routerLink: ['/tenants'],
                        'hasRoles': ['ROLE_SUPER_ADMIN'],
                    },
                    {
                        label: 'Users',
                        icon: 'pi pi-fw pi-globe',
                        routerLink: ['/users'],
                        'hasRoles': ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'],
                    },
                    {
                        separator: true,
                        'hasRoles': ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'],
                    },
                    {
                        label: 'Albums',
                        icon: 'pi pi-fw pi-globe',
                        routerLink: ['/albums']
                    },
                    {
                        label: 'Tracks',
                        icon: 'pi pi-fw pi-globe',
                        routerLink: ['/tracks']
                    },
                    {
                        label: 'Instruments',
                        icon: 'pi pi-fw pi-globe',
                        routerLink: ['/instruments'],
                        'hasRoles': ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_ARCHIVIST'],
                    }
                ]
            }
        ];
    }
}
