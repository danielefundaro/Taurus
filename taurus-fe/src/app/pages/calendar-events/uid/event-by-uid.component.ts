import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { CalendarFeedService } from '../../../service';

/**
 * The landing of a calendar feed link.
 *
 * <p>The ICS file carries the calendar UID of the event, not its database id, so the link lands
 * here: the UID is resolved and the browser is sent to the event page, replacing this step in the
 * history. An event that no longer exists, or that the account may not see, ends on a message
 * instead of an empty detail page.
 */
@Component({
    selector: 'app-event-by-uid',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './event-by-uid.component.html'
})
export class EventByUidComponent implements OnInit {
    protected failed = false;

    constructor(
        private readonly route: ActivatedRoute,
        private readonly router: Router,
        private readonly service: CalendarFeedService
    ) {}

    ngOnInit(): void {
        const uid = this.route.snapshot.paramMap.get('uid') ?? '';
        this.service
            .eventByUid(uid)
            .pipe(first())
            .subscribe({
                next: (event) => this.router.navigate(['/calendar', event.id], { replaceUrl: true }),
                error: () => (this.failed = true)
            });
    }

    protected openCalendar(): void {
        this.router.navigate(['/calendar']);
    }
}
