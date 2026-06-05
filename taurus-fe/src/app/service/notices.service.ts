import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Notices, NoticesCriteria } from '../module';
import { CommonService } from './common.service';

@Injectable({
    providedIn: 'root'
})
export class NoticesService extends CommonService<Notices, NoticesCriteria> {
    override resourceName(): string {
        return "notices";
    }

    public countUnread(): Observable<number> {
        return this.http.get<number>(`${this.baseUrl}/${this.resourceName()}/unread/count`);
    }

    public markAllAsRead(): Observable<Notices> {
        return this.http.patch<Notices>(`${this.baseUrl}/${this.resourceName()}/read-all`, {});
    }

    public markAsRead(id: number): Observable<Notices> {
        return this.http.patch<Notices>(`${this.baseUrl}/${this.resourceName()}/${id}/read`, {});
    }

    public deleteAll(): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${this.resourceName()}/delete-all`, {});
    }
}
