import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { BadgeModule } from 'primeng/badge';
import { OverlayBadgeModule } from 'primeng/overlaybadge';
import { StyleClassModule } from 'primeng/styleclass';
import { TooltipModule } from 'primeng/tooltip';
import { KeycloakService, LayoutService, TenantFeatureService } from '../../service';
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
        private readonly tenantFeatureService: TenantFeatureService
    ) { }

    ngOnInit(): void {
        this.tenantFeatureService.refresh().subscribe({ error: () => undefined });
    }

    protected get tenantName(): string {
        return this.tenantFeatureService.current()?.tenantName?.trim() ?? '';
    }

    protected get fullName(): string {
        const parts = [this.keycloakService.currentUserFirstName, this.keycloakService.currentUserLastName];
        return parts.filter(Boolean).join(' ');
    }

    protected toggleMenu(): void {
        this.layoutService.onMenuToggle();
    }

    protected toggleDarkMode(): void {
        this.layoutService.layoutConfig.update((state) => ({ ...state, darkTheme: !state.darkTheme }));
    }
}
