import { of } from 'rxjs';
import { UxTelemetryService } from './ux-telemetry.service';

describe('UxTelemetryService', () => {
    const client = () => {
        const http = jasmine.createSpyObj('HttpClient', ['post']);
        http.post.and.returnValue(of({}));
        return http;
    };

    it('sends the counts of one working session and starts over', () => {
        const http = client();
        const service = new UxTelemetryService(http);

        service.start('track-parts-workspace');
        service.record('move');
        service.record('move');
        service.record('merge');
        service.flush();

        const [url, body] = http.post.calls.mostRecent().args;
        expect(url).toContain('/ux-telemetry');
        expect(body.feature).toBe('track-parts-workspace');
        expect(body.events).toEqual({ open: 1, move: 2, merge: 1 });
        expect(body.durationMs).toBeGreaterThanOrEqual(0);
        expect(service.pending).toBe(0);
    });

    it('sends nothing when there is nothing to send', () => {
        const http = client();
        const service = new UxTelemetryService(http);

        service.flush();
        service.record('move');

        expect(http.post).not.toHaveBeenCalled();
        expect(service.snapshot()).toBeUndefined();
    });

    it('does not restart the clock when the same feature is opened again', () => {
        const http = client();
        const service = new UxTelemetryService(http);

        service.start('track-parts-workspace');
        service.record('move');
        service.start('track-parts-workspace');

        expect(http.post).not.toHaveBeenCalled();
        expect(service.snapshot()?.events).toEqual({ open: 1, move: 1 });
    });

    /** Un errore della misura non deve arrivare a chi sta lavorando. */
    it('ignores a failed send', () => {
        const http = jasmine.createSpyObj('HttpClient', ['post']);
        http.post.and.returnValue({ subscribe: ({ error }: { error: () => void }) => error() });
        const service = new UxTelemetryService(http);

        service.start('track-parts-workspace');

        expect(() => service.flush()).not.toThrow();
    });
});
