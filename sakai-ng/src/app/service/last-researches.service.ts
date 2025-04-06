import { Injectable } from '@angular/core';
import { LastResearches, LastResearchesCriteria } from '../module';
import { CommonService } from './common.service';

@Injectable({
    providedIn: 'root'
})
export class LastResearchesService extends CommonService<LastResearches, LastResearchesCriteria> {
    override resourceName(): string {
        return "last-research";
    }
}
