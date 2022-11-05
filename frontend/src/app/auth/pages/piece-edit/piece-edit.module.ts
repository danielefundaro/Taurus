import { NgModule } from '@angular/core';

import { PieceEditRoutingModule } from './piece-edit-routing.module';
import { DefaultModule } from '../default/default.module';


@NgModule({
    imports: [
        PieceEditRoutingModule,
        DefaultModule,
    ]
})
export class PieceEditModule { }
