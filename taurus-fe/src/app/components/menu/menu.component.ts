import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { RoleEnums } from '../../constants';
import { HasRolesDirective } from "../../directive";
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
                'hasRoles': [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER],
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
                        icon: 'pi pi-fw pi-building',
                        routerLink: ['/tenants'],
                        'hasRoles': [RoleEnums.SUPER_ADMIN],
                    },
                    {
                        label: 'Users',
                        icon: 'pi pi-fw pi-users',
                        routerLink: ['/users'],
                        'hasRoles': [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN],
                    },
                    {
                        separator: true,
                        'hasRoles': [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN],
                    },
                    {
                        label: 'Albums',
                        icon: 'pi pi-fw pi-book',
                        routerLink: ['/albums']
                    },
                    {
                        label: 'Tracks',
                        icon: 'pi pi-fw pi-file',
                        routerLink: ['/tracks']
                    },
                    {
                        label: 'Instruments',
                        icon: 'pi pi-fw pi-sliders-h',
                        routerLink: ['/instruments'],
                        'hasRoles': [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST],
                    }
                ]
            }
        ];
    }
}
