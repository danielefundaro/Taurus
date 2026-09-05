import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ConfirmationService } from 'primeng/api';
import { Notices, Page } from '../../../../module';
import { NotificationsWidgetComponent } from './notification-widget.component';

describe('NotificationsWidgetComponent', () => {
    let fixture: ComponentFixture<NotificationsWidgetComponent>;
    let component: NotificationsWidgetComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NotificationsWidgetComponent],
            providers: [ConfirmationService]
        }).compileComponents();
        fixture = TestBed.createComponent(NotificationsWidgetComponent);
        component = fixture.componentInstance;
    });

    function page(...notices: Partial<Notices>[]): Page<Notices> {
        return {
            content: notices.map((notice, index) => ({ id: index + 1, name: 'Notifica', ...notice }) as Notices),
            totalElements: notices.length
        } as Page<Notices>;
    }

    it('offers the per-category opt-out only on configurable notices', () => {
        const configurable = { id: 1, source: 'CALENDAR', preferencePolicy: 'CONFIGURABLE' } as Notices;
        const required = { id: 2, source: 'IDENTITY', preferencePolicy: 'REQUIRED' } as Notices;
        const legacy = { id: 3, source: 'INVENTORY' } as Notices;
        const sourceless = { id: 4 } as Notices;

        expect((component as any).canDisableCategory(configurable)).toBeTrue();
        expect((component as any).canDisableCategory(required)).toBeFalse();
        // Una riga senza politica esplicita è configurabile: l'allowlist REQUIRED nasce vuota.
        expect((component as any).canDisableCategory(legacy)).toBeTrue();
        expect((component as any).canDisableCategory(sourceless)).toBeFalse();
    });

    it('emits the notice when the opt-out button is pressed', () => {
        const notice = { id: 1, name: 'Prova', source: 'CALENDAR', preferencePolicy: 'CONFIGURABLE' } as Notices;
        component.notices = page(notice);
        component.view = 'ACTIVE';
        fixture.detectChanges();
        const emitted: Notices[] = [];
        component.disableCategory.subscribe((value) => emitted.push(value));

        const button = fixture.debugElement.query(By.css('button[aria-label^="Disattiva questa categoria"]'));
        expect(button).withContext('the opt-out button should be rendered').not.toBeNull();
        button.nativeElement.click();

        expect(emitted.map((value) => value.id)).toEqual([1]);
    });

    it('does not render the opt-out button on a required notice', () => {
        component.notices = page({ id: 1, source: 'IDENTITY', preferencePolicy: 'REQUIRED' });
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('button[aria-label^="Disattiva questa categoria"]'))).toBeNull();
    });

    it('snoozes for one hour and for the next morning with backend-checkable instants', () => {
        const notice = { id: 1, name: 'Prova', source: 'CALENDAR' } as Notices;
        const emitted: { notice: Notices; until: Date }[] = [];
        component.snooze.subscribe((value) => emitted.push(value));

        (component as any).snoozeForOneHour(notice);
        (component as any).snoozeUntilTomorrow(notice);

        expect(emitted).toHaveSize(2);
        const inOneHour = emitted[0].until.getTime() - Date.now();
        expect(inOneHour).toBeGreaterThan(59 * 60 * 1000);
        expect(inOneHour).toBeLessThanOrEqual(60 * 60 * 1000 + 1000);
        expect(emitted[1].until.getTime()).toBeGreaterThan(Date.now());
    });

    it('only emits a custom snooze once an instant has been chosen', () => {
        const notice = { id: 7, name: 'Prova' } as Notices;
        const emitted: { notice: Notices; until: Date }[] = [];
        component.snooze.subscribe((value) => emitted.push(value));

        (component as any).applyCustomSnooze(notice);
        expect(emitted).toHaveSize(0);

        const chosen = new Date(Date.now() + 3 * 60 * 60 * 1000);
        (component as any).customSnooze[7] = chosen;
        (component as any).applyCustomSnooze(notice);

        expect(emitted).toHaveSize(1);
        expect(emitted[0].until).toEqual(chosen);
    });

    it('shows the return instant and the show-now action in the snoozed view', () => {
        component.notices = page({ id: 1, source: 'CALENDAR', snoozedUntil: new Date(Date.now() + 3600_000) });
        component.view = 'SNOOZED';
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Ritorna');
        expect(fixture.debugElement.query(By.css('button[aria-label^="Mostra ora"]'))).not.toBeNull();
        // Le azioni di rinvio appartengono soltanto alla vista attiva.
        expect(fixture.debugElement.query(By.css('button[aria-label^="Ricordamelo tra"]'))).toBeNull();
    });

    it('hides the snooze actions on a notice that is already read', () => {
        component.notices = page({ id: 1, source: 'CALENDAR', readDate: new Date() });
        component.view = 'ACTIVE';
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('button[aria-label^="Ricordamelo tra"]'))).toBeNull();
    });
});
