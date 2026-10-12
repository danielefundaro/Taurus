import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { FloatingConfiguratorComponent } from '../../components/floating-configurator/floating-configurator.component';
import { KeycloakService, TenantStatusService } from '../../service';

/** Mostrata a chi accede con un tenant che non ha ancora uno schema e non può crearlo. */
@Component({
    selector: 'app-tenant-unavailable',
    standalone: true,
    imports: [ButtonModule, FloatingConfiguratorComponent],
    templateUrl: './tenant-unavailable.component.html',
    styleUrl: './tenant-unavailable.component.scss'
})
export class TenantUnavailableComponent {
    constructor(
        private readonly keycloakService: KeycloakService,
        private readonly tenantStatusService: TenantStatusService,
        private readonly router: Router
    ) {}

    protected get tenantCode(): string | null {
        return this.tenantStatusService.tenantCode();
    }

    protected retry(): void {
        this.tenantStatusService.invalidate();
        this.router.navigate(['/']);
    }

    protected logout(): void {
        this.keycloakService.logout();
    }
}
