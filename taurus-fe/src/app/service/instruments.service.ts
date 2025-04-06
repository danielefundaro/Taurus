import { Injectable } from '@angular/core';
import { Instruments, InstrumentsCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

@Injectable({
    providedIn: 'root'
})
export class InstrumentsService extends CommonOpenSearchService<Instruments, InstrumentsCriteria> {
    override resourceName(): string {
        return "instruments";
    }
}
