import { Injectable } from '@angular/core';
import { Notices, NoticesCriteria } from '../module';
import { CommonService } from './common.service';

@Injectable({
    providedIn: 'root'
})
export class PreferencesService extends CommonService<Notices, NoticesCriteria> {
    override resourceName(): string {
        return "notices";
    }
}
