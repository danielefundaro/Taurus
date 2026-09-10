import { CommonModule } from '@angular/common';
import { Component, effect, Renderer2, ViewChild } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { FooterComponent } from '../../components/footer/footer.component';
import { InlineAlertComponent } from '../../components/inline-alert/inline-alert.component';
import { SidebarComponent } from '../../components/sidebar/sidebar.component';
import { TopbarComponent } from '../../components/topbar/topbar.component';
import { LayoutService, NotificationCenterService, PushNotificationService, TenantFeatureService, ToastService } from '../../service';

@Component({
    selector: 'app-layout',
    standalone: true,
    imports: [CommonModule, ButtonModule, InlineAlertComponent, TopbarComponent, SidebarComponent, RouterModule, FooterComponent],
    templateUrl: './layout.component.html',
    styleUrl: './layout.component.scss'
})
export class LayoutComponent {
    /** Invito non invasivo ad attivare i promemoria push: mai un prompt automatico del browser. */
    protected pushPromptVisible: boolean;

    private static readonly PUSH_PROMPT_DISMISSED = 'pushPromptDismissed';

    overlayMenuOpenSubscription: Subscription;

    menuOutsideClickListener: any;

    @ViewChild(SidebarComponent) appSidebar!: SidebarComponent;

    @ViewChild(TopbarComponent) appTopBar!: TopbarComponent;

    constructor(
        public layoutService: LayoutService,
        private readonly notificationCenter: NotificationCenterService,
        private readonly pushNotificationService: PushNotificationService,
        private readonly tenantFeatureService: TenantFeatureService,
        private readonly toastService: ToastService,
        public renderer: Renderer2,
        public router: Router
    ) {
        this.pushPromptVisible = this.pushNotificationService.permission === 'default' && sessionStorage.getItem(LayoutComponent.PUSH_PROMPT_DISMISSED) !== 'true';
        this.notificationCenter.start();
        this.tenantFeatureService.refresh().subscribe({ error: () => undefined });
        effect(() => {
            if (!this.tenantFeatureService.loaded()) return;
            const url = this.router.url;
            if ((url.startsWith('/finance') && !this.tenantFeatureService.financeEnabled()) || (url.startsWith('/inventory') && !this.tenantFeatureService.inventoryEnabled())) {
                this.router.navigate(['/']);
            }
        });
        this.overlayMenuOpenSubscription = this.layoutService.overlayOpen$.subscribe(() => {
            this.menuOutsideClickListener ??= this.renderer.listen('document', 'click', (event) => {
                if (this.isOutsideClicked(event)) {
                    this.hideMenu();
                }
            });

            if (this.layoutService.layoutState().staticMenuMobileActive) {
                this.blockBodyScroll();
            }
        });

        this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
            this.hideMenu();
            this.tenantFeatureService.refresh().subscribe({ error: () => undefined });
        });
    }

    isOutsideClicked(event: MouseEvent) {
        const sidebarEl = document.querySelector('.layout-sidebar');
        const topbarEl = document.querySelector('.layout-menu-button');
        const eventTarget = event.target as Node;

        return !(sidebarEl?.isSameNode(eventTarget) || sidebarEl?.contains(eventTarget) || topbarEl?.isSameNode(eventTarget) || topbarEl?.contains(eventTarget));
    }

    hideMenu() {
        this.layoutService.layoutState.update((prev) => ({ ...prev, overlayMenuActive: false, staticMenuMobileActive: false, menuHoverActive: false }));
        if (this.menuOutsideClickListener) {
            this.menuOutsideClickListener();
            this.menuOutsideClickListener = null;
        }
        this.unblockBodyScroll();
    }

    blockBodyScroll(): void {
        if (document.body.classList) {
            document.body.classList.add('blocked-scroll');
        } else {
            document.body.className += ' blocked-scroll';
        }
    }

    unblockBodyScroll(): void {
        if (document.body.classList) {
            document.body.classList.remove('blocked-scroll');
        } else {
            document.body.className = document.body.className.replace(new RegExp('(^|\\b)' + 'blocked-scroll'.split(' ').join('|') + '(\\b|$)', 'gi'), ' ');
        }
    }

    get containerClass() {
        return {
            'layout-overlay': this.layoutService.layoutConfig().menuMode === 'overlay',
            'layout-static': this.layoutService.layoutConfig().menuMode === 'static',
            'layout-static-inactive': this.layoutService.layoutState().staticMenuDesktopInactive && this.layoutService.layoutConfig().menuMode === 'static',
            'layout-overlay-active': this.layoutService.layoutState().overlayMenuActive,
            'layout-mobile-active': this.layoutService.layoutState().staticMenuMobileActive
        };
    }

    protected enablePush(): void {
        this.pushNotificationService.requestPermissionAndSubscribe().then((enabled) => {
            this.pushPromptVisible = false;
            if (enabled) this.toastService.success('Notifiche attive', 'Riceverai un promemoria prima degli eventi a cui hai dato disponibilità.');
            else this.toastService.info('Notifiche non attivate', 'Puoi attivarle in qualsiasi momento dal tuo profilo.');
        });
    }

    protected dismissPushPrompt(): void {
        this.pushPromptVisible = false;
        sessionStorage.setItem(LayoutComponent.PUSH_PROMPT_DISMISSED, 'true');
    }

    ngOnDestroy() {
        this.notificationCenter.stop();
        if (this.overlayMenuOpenSubscription) {
            this.overlayMenuOpenSubscription.unsubscribe();
        }

        if (this.menuOutsideClickListener) {
            this.menuOutsideClickListener();
        }
    }
}
