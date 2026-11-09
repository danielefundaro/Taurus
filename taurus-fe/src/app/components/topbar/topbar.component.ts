import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnInit, ViewChild } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { BadgeModule } from 'primeng/badge';
import { OverlayBadgeModule } from 'primeng/overlaybadge';
import { StyleClassModule } from 'primeng/styleclass';
import { TooltipModule } from 'primeng/tooltip';
import { RoleLabelsMap } from '../../constants';
import { TourAnchorDirective } from '../../directive';
import { GuidedTourService, KeycloakService, LayoutService, TenantFeatureService, TenantStatusService } from '../../service';
import { ConfiguratorComponent } from '../configurator/configurator.component';

@Component({
    selector: 'app-topbar',
    standalone: true,
    imports: [RouterModule, CommonModule, StyleClassModule, BadgeModule, OverlayBadgeModule, TooltipModule, ConfiguratorComponent, TourAnchorDirective],
    templateUrl: './topbar.component.html',
    styleUrl: './topbar.component.scss'
})
export class TopbarComponent implements OnInit {
    items!: MenuItem[];

    /** Help menu: the guided tour, the guide of the current page and, in a next phase, the news. */
    protected helpMenuOpen = false;
    /** Shown once the catalog of the page guides says the current page has one for the session. */
    protected pageGuideAvailable = false;

    @ViewChild('help') private help?: ElementRef<HTMLElement>;

    constructor(
        protected layoutService: LayoutService,
        private readonly keycloakService: KeycloakService,
        private readonly tenantFeatureService: TenantFeatureService,
        private readonly tenantStatusService: TenantStatusService,
        protected readonly guidedTour: GuidedTourService
    ) { }

    ngOnInit(): void {
        this.tenantFeatureService.refresh().subscribe({ error: () => undefined });
    }

    protected get bootstrap(): boolean {
        return this.tenantStatusService.bootstrap();
    }

    /** Hidden until the flags are read, like the menu: the shortcut must not flash and then disappear. */
    protected get calendarEnabled(): boolean {
        return this.tenantFeatureService.loaded() && this.tenantFeatureService.calendarEnabled();
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

    /** Includes the unread count shown by the badge: the accessible name must contain the visible text. */
    protected get notificationsLabel(): string {
        const unread = this.layoutService.notificationNumber();
        return unread ? `Notifiche, ${unread} da leggere` : 'Notifiche';
    }

    protected get fullName(): string {
        const parts = [this.keycloakService.currentUserFirstName, this.keycloakService.currentUserLastName];
        return parts.filter(Boolean).join(' ');
    }

    protected toggleHelpMenu(): void {
        this.helpMenuOpen = !this.helpMenuOpen;
        if (!this.helpMenuOpen) return;
        this.pageGuideAvailable = false;
        this.guidedTour.pageGuideAvailable().then((available) => (this.pageGuideAvailable = available && this.helpMenuOpen));
        setTimeout(() => this.help?.nativeElement.querySelector<HTMLElement>('[role="menuitem"]')?.focus());
    }

    protected openGuidedTour(): void {
        this.helpMenuOpen = false;
        this.guidedTour.open();
    }

    protected openPageGuide(): void {
        this.helpMenuOpen = false;
        this.guidedTour.openPage();
    }

    @HostListener('document:click', ['$event'])
    protected closeHelpMenuOutside(event: MouseEvent): void {
        if (this.helpMenuOpen && !this.help?.nativeElement.contains(event.target as Node)) this.helpMenuOpen = false;
    }

    @HostListener('document:keydown.escape')
    protected closeHelpMenu(): void {
        if (!this.helpMenuOpen) return;
        this.helpMenuOpen = false;
        this.help?.nativeElement.querySelector<HTMLElement>('button')?.focus();
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
