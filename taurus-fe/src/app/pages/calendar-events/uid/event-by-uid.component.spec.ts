import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CalendarFeedService } from '../../../service';
import { EventByUidComponent } from './event-by-uid.component';

describe('EventByUidComponent', () => {
    const uid = 'd1f4c0de-0000-4000-8000-000000000001';
    let service: jasmine.SpyObj<CalendarFeedService>;
    let router: jasmine.SpyObj<Router>;

    beforeEach(async () => {
        service = jasmine.createSpyObj<CalendarFeedService>('CalendarFeedService', ['eventByUid']);
        router = jasmine.createSpyObj<Router>('Router', ['navigate']);
        await TestBed.configureTestingModule({
            imports: [EventByUidComponent],
            providers: [provideNoopAnimations(), { provide: CalendarFeedService, useValue: service }, { provide: Router, useValue: router }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ uid }) } } }]
        }).compileComponents();
    });

    it('sends the browser to the event of the link without leaving a step in the history', () => {
        service.eventByUid.and.returnValue(of({ id: 42 }));

        const fixture: ComponentFixture<EventByUidComponent> = TestBed.createComponent(EventByUidComponent);
        fixture.detectChanges();

        expect(service.eventByUid).toHaveBeenCalledWith(uid);
        expect(router.navigate).toHaveBeenCalledWith(['/calendar', 42], { replaceUrl: true });
    });

    it('explains that the event is no longer available instead of opening an empty page', () => {
        service.eventByUid.and.returnValue(throwError(() => new Error('not found')));

        const fixture: ComponentFixture<EventByUidComponent> = TestBed.createComponent(EventByUidComponent);
        fixture.detectChanges();

        expect(router.navigate).not.toHaveBeenCalled();
        expect(fixture.nativeElement.textContent).toContain('Evento non disponibile');
        expect(fixture.nativeElement.textContent).toContain('copia più vecchia');
    });
});
