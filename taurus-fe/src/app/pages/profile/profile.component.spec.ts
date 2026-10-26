import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { of, throwError } from 'rxjs';
import { NotificationPreferences } from '../../module';
import { ConfirmService, KeycloakService, NotificationPreferencesService, PushNotificationService, PushPermission, TenantFeatureService, ToastService, UsersService } from '../../service';
import { ProfileComponent } from './profile.component';

type Pushes = jasmine.SpyObj<PushNotificationService>;

const preferences = (overrides: Partial<NotificationPreferences> = {}): NotificationPreferences => ({
    version: 2,
    timeZone: 'Europe/Rome',
    eventRemindersEnabled: true,
    defaultCalendarReminderMinutes: 30,
    quietHours: { enabled: false, start: '22:00:00', end: '07:00:00' },
    pushPausedUntil: null,
    digestLocalTime: '08:00:00',
    pushPreview: 'PRIVATE',
    categories: [
        { source: 'CALENDAR', inAppEnabled: true, pushMode: 'IMMEDIATE' },
        { source: 'INVENTORY', inAppEnabled: true, pushMode: 'OFF' },
        { source: 'FINANCE', inAppEnabled: false, pushMode: 'DAILY_DIGEST' }
    ],
    ...overrides
});

describe('ProfileComponent notification preferences', () => {
    beforeEach(() => TestBed.configureTestingModule({ providers: [{ provide: Keycloak, useValue: {} }] }));

    function create(initial: NotificationPreferences = preferences(), browser: { supported: boolean; permission: PushPermission } = { supported: true, permission: 'default' }): {
        component: ProfileComponent;
        preferencesService: jasmine.SpyObj<NotificationPreferencesService>;
        pushNotificationService: Pushes;
        toastService: jasmine.SpyObj<ToastService>;
    } {
        const preferencesService = jasmine.createSpyObj<NotificationPreferencesService>('NotificationPreferencesService', ['getPreferences', 'savePreferences']);
        preferencesService.getPreferences.and.returnValue(of(initial));
        preferencesService.savePreferences.and.returnValue(of(initial));

        const pushNotificationService = jasmine.createSpyObj<PushNotificationService>('PushNotificationService', ['refreshState', 'requestPermissionAndSubscribe', 'unsubscribe'], browser);
        pushNotificationService.refreshState.and.returnValue(Promise.resolve(false));

        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'warn']);
        const features = {
            notificationPreferencesEnabled: () => true,
            webPushRemindersEnabled: () => true,
            inventorySignatureEnabled: () => false,
            externalCalendarFeedEnabled: () => false,
            refresh: () => of(null)
        } as unknown as TenantFeatureService;

        const component = TestBed.runInInjectionContext(
            () =>
                new ProfileComponent(
                    { isUser: true, isUserExternal: false } as unknown as KeycloakService,
                    {} as UsersService,
                    {} as ConfirmService,
                    toastService,
                    pushNotificationService,
                    preferencesService,
                    features
                )
        );
        component['loadNotificationPreferences']();
        return { component, preferencesService, pushNotificationService, toastService };
    }

    it('loads the aggregate and trims the stored times to the minute the form shows', () => {
        const { component } = create();

        expect(component['notificationPreferences']!.timeZone).toBe('Europe/Rome');
        expect(component['notificationPreferences']!.quietHours.start).toBe('22:00');
        expect(component['notificationPreferences']!.digestLocalTime).toBe('08:00');
        expect(component['notificationPreferencesDirty']).toBeFalse();
        expect(component['notificationPreferencesInvalid']).toBeFalse();
    });

    it('labels every category of the matrix and reads the digest from the modes chosen', () => {
        const { component } = create();
        const categories = component['notificationPreferences']!.categories;

        expect(categories.map((category) => component['categoryLabel'](category))).toEqual(['Calendario', 'Inventario', 'Economia']);
        expect(component['usesDigest']).toBeTrue();

        categories.forEach((category) => (category.pushMode = 'OFF'));
        expect(component['usesDigest']).toBeFalse();
    });

    // Lo stato sporco è quello che alimenta la guardia di uscita: un cambio della matrice deve contare.
    it('becomes dirty on a change of the matrix and clean again after the save', () => {
        const { component, preferencesService } = create();

        component['notificationPreferences']!.categories[1].pushMode = 'IMMEDIATE';
        component['notificationPreferenceChanged']();

        expect(component['notificationPreferencesDirty']).toBeTrue();
        expect(component['isUnitDirty']('notifiche')).toBeTrue();

        preferencesService.savePreferences.and.returnValue(of(preferences({ version: 3 })));
        component['saveNotificationPreferences']();

        expect(component['notificationPreferences']!.version).toBe(3);
        expect(component['notificationPreferencesDirty']).toBeFalse();
        expect(component['isUnitDirty']('notifiche')).toBeFalse();
        expect(component['savingNotifications']).toBeFalse();
    });

    it('refuses to send a form the backend would reject', () => {
        const { component, preferencesService } = create();

        component['notificationPreferences']!.timeZone = '   ';
        expect(component['notificationPreferencesInvalid']).toBeTrue();

        component['notificationPreferences']!.timeZone = 'Europe/Rome';
        component['notificationPreferences']!.defaultCalendarReminderMinutes = 1441;
        expect(component['notificationPreferencesInvalid']).toBeTrue();

        component['notificationPreferences']!.defaultCalendarReminderMinutes = 30;
        component['notificationPreferences']!.quietHours = { enabled: true, start: '22:00', end: '22:00' };
        expect(component['notificationPreferencesInvalid']).toBeTrue();

        // Il riepilogo dentro il silenzio non partirebbe mai: la pagina lo blocca prima della PUT.
        component['notificationPreferences']!.quietHours = { enabled: true, start: '22:00', end: '09:00' };
        component['notificationPreferences']!.digestLocalTime = '08:00';
        expect(component['digestInsideQuietHours']).toBeTrue();
        expect(component['notificationPreferencesInvalid']).toBeTrue();

        component['saveNotificationPreferences']();
        expect(preferencesService.savePreferences).not.toHaveBeenCalled();
    });

    it('keeps the pending change when a concurrent save loses the version conflict', () => {
        const { component, preferencesService, toastService } = create();

        component['notificationPreferences']!.defaultCalendarReminderMinutes = 45;
        component['notificationPreferenceChanged']();
        preferencesService.savePreferences.and.returnValue(throwError(() => ({ status: 409 })));

        component['saveNotificationPreferences']();

        expect(component['savingNotifications']).toBeFalse();
        expect(component['notificationPreferences']!.defaultCalendarReminderMinutes).toBe(45);
        expect(component['notificationPreferencesDirty']).toBeTrue();
        expect(toastService.success).not.toHaveBeenCalled();
    });

    it('explains the state of the browser instead of offering a switch that cannot work', () => {
        expect(create().component['pushSupported']).toBeTrue();
        expect(create().component['pushDenied']).toBeFalse();
        expect(create().component['pushHint']).not.toContain('bloccate');

        const unsupported = create(preferences(), { supported: false, permission: 'unsupported' }).component;
        expect(unsupported['pushSupported']).toBeFalse();
        expect(unsupported['pushHint']).toBe('Questo browser non supporta le notifiche push.');

        const denied = create(preferences(), { supported: true, permission: 'denied' }).component;
        expect(denied['pushDenied']).toBeTrue();
        expect(denied['pushHint']).toContain('bloccate');
    });

    it('turns a refused permission into an error instead of a false confirmation', async () => {
        const { component, pushNotificationService, toastService } = create();
        pushNotificationService.requestPermissionAndSubscribe.and.returnValue(Promise.resolve(false));

        component['onPushToggle'](true);
        await Promise.resolve();
        await Promise.resolve();

        expect(component['pushEnabled']).toBeFalse();
        expect(component['pushBusy']).toBeFalse();
        expect(toastService.warn).toHaveBeenCalled();
        expect(toastService.success).not.toHaveBeenCalled();
    });

    it('clears the pause of the pushes and reports it in the aggregate', () => {
        const { component } = create(preferences({ pushPausedUntil: '2026-10-27T08:00:00.000Z' }));

        expect(component['pushPausedUntil']).toEqual(new Date('2026-10-27T08:00:00.000Z'));

        component['clearPushPause']();

        expect(component['pushPausedUntil']).toBeUndefined();
        expect(component['notificationPreferences']!.pushPausedUntil).toBeNull();
        expect(component['notificationPreferencesDirty']).toBeTrue();
    });
});
