import { CommonModule } from '@angular/common';
import { Component, HostBinding, Input, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { isActive, NavigationEnd, Router, RouterModule } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { RippleModule } from 'primeng/ripple';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { LayoutService } from '../../service';
import { HasRolesDirective } from './../../directive';

@Component({
    selector: '[app-menuitem]',
    imports: [CommonModule, RouterModule, RippleModule, HasRolesDirective],
    templateUrl: './menu-item.component.html',
    styleUrl: './menu-item.component.scss',
    changeDetection: ChangeDetectionStrategy.Eager,
    providers: [LayoutService]
})
export class MenuItemComponent implements OnInit, OnDestroy {
    @Input() item!: MenuItem;

    @Input() index!: number;

    @Input() @HostBinding('class.layout-root-menuitem') root!: boolean;

    @Input() parentKey!: string;

    /** Anchor of the guided tour declared by the item of the menu model (`tourAnchor`). */
    @HostBinding('attr.data-tour-anchor') get tourAnchor(): string | null {
        return this.item?.['tourAnchor'] ?? null;
    }

    active = false;

    menuSourceSubscription: Subscription;

    menuResetSubscription: Subscription;

    key: string = '';

    constructor(
        public router: Router,
        private readonly layoutService: LayoutService
    ) {
        this.menuSourceSubscription = this.layoutService.menuSource$.subscribe((value) => {
            Promise.resolve(null).then(() => {
                if (value.routeEvent) {
                    this.active = !!(value.key === this.key || value.key.startsWith(this.key + '-'));
                } else if (value.key !== this.key && !value.key.startsWith(this.key + '-')) {
                    this.active = false;
                }
            });
        });

        this.menuResetSubscription = this.layoutService.resetSource$.subscribe(() => {
            this.active = false;
        });

        this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe((params) => {
            if (this.item.routerLink) {
                this.updateActiveStateFromRoute();
            }
        });
    }

    ngOnInit() {
        this.key = this.parentKey ? this.parentKey + '-' + this.index : String(this.index);

        if (this.item.routerLink) {
            this.updateActiveStateFromRoute();
        }
    }

    updateActiveStateFromRoute() {
        const activeRoute = isActive(this.item.routerLink[0], this.router, { paths: 'exact', queryParams: 'ignored', matrixParams: 'ignored', fragment: 'ignored' })();

        if (activeRoute) {
            this.layoutService.onMenuStateChange({ key: this.key, routeEvent: true });
        }
    }

    itemClick(event: Event) {
        // avoid processing disabled items
        if (this.item.disabled) {
            event.preventDefault();
            return;
        }

        // execute command
        if (this.item.command) {
            this.item.command({ originalEvent: event, item: this.item });
        }

        // toggle active state
        if (this.item.items) {
            this.active = !this.active;
        }

        this.layoutService.onMenuStateChange({ key: this.key });
    }

    @HostBinding('class.active-menuitem')
    get activeClass() {
        return this.active && !this.root;
    }

    ngOnDestroy() {
        if (this.menuSourceSubscription) {
            this.menuSourceSubscription.unsubscribe();
        }

        if (this.menuResetSubscription) {
            this.menuResetSubscription.unsubscribe();
        }
    }
}
