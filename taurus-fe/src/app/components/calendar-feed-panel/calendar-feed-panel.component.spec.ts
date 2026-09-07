import { of } from 'rxjs';
import { CalendarFeedPanelComponent } from './calendar-feed-panel.component';

describe('CalendarFeedPanelComponent', () => {
    it('shows the required-name error without sending an invalid request', () => {
        const { component, service } = setup();

        (component as any).create();

        expect((component as any).nameTouched).toBeTrue();
        expect(service.create).not.toHaveBeenCalled();
    });

    it('creates the feed and exposes the one-time link when the name is present', () => {
        const { component, service, toast } = setup();
        (component as any).draft.name = 'Calendario eventi';

        (component as any).create();

        expect(service.create).toHaveBeenCalled();
        expect((component as any).secret.subscriptionUrl).toContain('/calendar.ics');
        expect((component as any).nameTouched).toBeFalse();
        expect(toast.success).toHaveBeenCalledWith('Feed creato', jasmine.stringContaining('non potrai recuperarlo'));
    });

    it('removes a revoked feed after destructive confirmation', () => {
        const { component, service, toast } = setup();
        const feed = { id: 'feed-id', status: 'REVOKED' };

        (component as any).remove(feed);

        expect(service.remove).toHaveBeenCalledWith('feed-id', false);
        expect(service.list).toHaveBeenCalled();
        expect(toast.success).toHaveBeenCalledWith('Feed eliminato', jasmine.any(String));
    });
});

function setup() {
    const secret = {
        id: 'feed-id',
        name: 'Calendario eventi',
        feedType: 'TENANT',
        visibilityScope: 'INTERNAL',
        detailLevel: 'MINIMAL',
        pastDays: 90,
        futureMonths: 18,
        subscriptionUrl: 'https://example.test/calendar.ics',
        tokenShownOnce: true,
        createdAt: '2026-09-07T10:00:00Z'
    };
    const service = {
        create: jasmine.createSpy().and.returnValue(of(secret)),
        list: jasmine.createSpy().and.returnValue(of([])),
        remove: jasmine.createSpy().and.returnValue(of(undefined))
    };
    const confirm = {
        confirmDestructive: jasmine.createSpy().and.callFake((options: { accept: () => void }) => options.accept())
    };
    const toast = {
        success: jasmine.createSpy(),
        error: jasmine.createSpy()
    };
    const component = new CalendarFeedPanelComponent(service as never, confirm as never, toast as never);
    return { component, service, toast };
}
