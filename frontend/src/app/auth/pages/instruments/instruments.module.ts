import { NgModule } from '@angular/core';

import { InstrumentsRoutingModule } from './instruments-routing.module';
import { DefaultModule } from '../default/default.module';


@NgModule({
    imports: [
        InstrumentsRoutingModule,
        DefaultModule
    ]
})
export class InstrumentsModule { }
