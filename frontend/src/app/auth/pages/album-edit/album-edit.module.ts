import { NgModule } from '@angular/core';

import { AlbumEditRoutingModule } from './album-edit-routing.module';
import { DefaultModule } from '../default/default.module';


@NgModule({
    imports: [
        AlbumEditRoutingModule,
        DefaultModule
    ]
})
export class AlbumEditModule { }
