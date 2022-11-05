import { NgModule } from '@angular/core';

import { PiecesRoutingModule } from './pieces-routing.module';
import { DefaultModule } from '../default/default.module';


@NgModule({
    imports: [
        PiecesRoutingModule,
        DefaultModule
    ]
})
export class PiecesModule { }
