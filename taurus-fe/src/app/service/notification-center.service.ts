import { Injectable } from '@angular/core';
import { EMPTY, fromEvent, merge, Subject, Subscription, switchMap, timer } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LayoutService } from './layout.service';
import { NoticesService } from './notices.service';

@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
    private readonly refreshRequest = new Subject<void>();
    private subscription?: Subscription;

    constructor(
        private readonly noticesService: NoticesService,
        private readonly layoutService: LayoutService
    ) {}

    start(): void {
        if (this.subscription) return;
        this.subscription = merge(timer(0, 30_000), fromEvent(window, 'focus'), this.refreshRequest)
            .pipe(switchMap(() => this.noticesService.countUnread().pipe(catchError(() => EMPTY))))
            .subscribe((count) => this.layoutService.notificationNumber.set(count));
    }

    refresh(): void {
        this.refreshRequest.next();
    }

    stop(): void {
        this.subscription?.unsubscribe();
        this.subscription = undefined;
    }
}
