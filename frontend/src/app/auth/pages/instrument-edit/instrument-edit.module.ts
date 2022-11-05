import { NgModule } from '@angular/core';

import { InstrumentEditRoutingModule } from './instrument-edit-routing.module';
import { DefaultModule } from '../default/default.module';


@NgModule({
    imports: [
        InstrumentEditRoutingModule,
        DefaultModule
    ]
})
export class InstrumentEditModule { }
