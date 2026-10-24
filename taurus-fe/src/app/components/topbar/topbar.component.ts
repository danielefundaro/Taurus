import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { BadgeModule } from 'primeng/badge';
import { OverlayBadgeModule } from 'primeng/overlaybadge';
import { StyleClassModule } from 'primeng/styleclass';
import { TooltipModule } from 'primeng/tooltip';
import { RoleLabelsMap } from '../../constants';
import { KeycloakService, LayoutService, TenantFeatureService, TenantStatusService } from '../../service';
import { ConfiguratorComponent } from '../configurator/configurator.component';

@Component({
    selector: 'app-topbar',
    standalone: true,
    imports: [RouterModule, CommonModule, StyleClassModule, BadgeModule, OverlayBadgeModule, TooltipModule, ConfiguratorComponent],
    templateUrl: './topbar.component.html',
    styleUrl: './topbar.component.scss'
})
export class TopbarComponent implements OnInit {
    items!: MenuItem[];

    constructor(
        protected layoutService: LayoutService,
        private readonly keycloakService: KeycloakService,
        private readonly tenantFeatureService: TenantFeatureService,
        private readonly tenantStatusService: TenantStatusService
    ) { }

    ngOnInit(): void {
        this.tenantFeatureService.refresh().subscribe({ error: () => undefined });
    }

    protected get bootstrap(): boolean {
        return this.tenantStatusService.bootstrap();
    }

    protected get tenantName(): string {
        if (this.bootstrap) return 'Primo accesso';
        return this.tenantFeatureService.current()?.tenantName?.trim() ?? '';
    }

    /** The session role, shown only when the user chose it among more roles of the tenant. */
    protected get roleLabel(): string {
        if (this.bootstrap || !this.keycloakService.currentUserRoleChosen) return '';
        const role = this.keycloakService.currentUserRole;
        return RoleLabelsMap.find((label) => label.code === role)?.name ?? '';
    }

    protected get tenantTitle(): string {
        return this.roleLabel ? `${this.tenantName} · ${this.roleLabel}` : this.tenantName;
    }

    protected get fullName(): string {
        const parts = [this.keycloakService.currentUserFirstName, this.keycloakService.currentUserLastName];
        return parts.filter(Boolean).join(' ');
    }

    protected logout(): void {
        this.keycloakService.logout();
    }

    protected toggleMenu(): void {
        this.layoutService.onMenuToggle();
    }

    protected toggleDarkMode(): void {
        this.layoutService.layoutConfig.update((state) => ({ ...state, darkTheme: !state.darkTheme }));
    }
}
