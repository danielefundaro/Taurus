import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { StyleClassModule } from 'primeng/styleclass';
import { KeycloakService, LayoutService } from '../../service';
import { ConfiguratorComponent } from '../configurator/configurator.component';

@Component({
    selector: 'app-topbar',
    standalone: true,
    imports: [
        RouterModule,
        CommonModule,
        StyleClassModule,
        ConfiguratorComponent
    ],
    templateUrl: './topbar.component.html',
    styleUrl: './topbar.component.scss',
})
export class TopbarComponent {
    items!: MenuItem[];

    constructor(
        protected layoutService: LayoutService,
        private readonly keycloakService: KeycloakService,
    ) { }

    protected toggleMenu(): void {
        this.layoutService.onMenuToggle();
    }

    protected toggleDarkMode(): void {
        this.layoutService.layoutConfig.update((state) => ({ ...state, darkTheme: !state.darkTheme }));
    }

    protected accountManagement(): void {
        this.keycloakService.accountManagement();
    }
}
