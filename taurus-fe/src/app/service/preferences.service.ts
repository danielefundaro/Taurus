import { Injectable } from '@angular/core';
import { Preferences, PreferencesCriteria } from '../module';
import { CommonService } from './common.service';

@Injectable({
    providedIn: 'root'
})
export class PreferencesService extends CommonService<Preferences, PreferencesCriteria> {
    override resourceName(): string {
        return "preferences";
    }
}
