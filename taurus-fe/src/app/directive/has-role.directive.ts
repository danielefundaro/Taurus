// has-role.directive.ts
import { Directive, Input, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { KeycloakService } from '../service';

@Directive({
    selector: '[hasRoles]',
    standalone: true
})
export class HasRolesDirective implements OnInit {
    @Input() hasRoles!: string | string[];
    @Input() hasRolesElse?: TemplateRef<any>;

    constructor(
        private readonly templateRef: TemplateRef<any>,
        private readonly viewContainer: ViewContainerRef,
        private readonly keycloakService: KeycloakService,
    ) { }

    ngOnInit() {
        this.updateView();
    }

    private updateView() {
        const roles = Array.isArray(this.hasRoles) ? this.hasRoles : [this.hasRoles];
        const hasPermission = roles.includes(this.keycloakService.currentUserRole);

        this.viewContainer.clear();

        if (hasPermission) {
            this.viewContainer.createEmbeddedView(this.templateRef);
        } else if (this.hasRolesElse) {
            this.viewContainer.createEmbeddedView(this.hasRolesElse);
        }
    }
}