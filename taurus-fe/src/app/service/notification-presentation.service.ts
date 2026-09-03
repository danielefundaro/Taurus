import { Injectable } from '@angular/core';

const SOURCE_ICONS: Readonly<Record<string, string>> = {
    GENERAL: 'pi pi-bell',
    CONTENT: 'pi pi-file',
    CALENDAR: 'pi pi-calendar',
    IDENTITY: 'pi pi-users',
    TENANT: 'pi pi-building',
    INVENTORY: 'pi pi-box',
    FINANCE: 'pi pi-wallet'
};

@Injectable({ providedIn: 'root' })
export class NotificationPresentationService {
    icon(source?: string): string {
        return SOURCE_ICONS[source ?? 'GENERAL'] ?? SOURCE_ICONS['GENERAL'];
    }
}
