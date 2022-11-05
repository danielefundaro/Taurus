import { NgModule } from '@angular/core';

import { AlbumsRoutingModule } from './album-routing.module';
import { DefaultModule } from '../default/default.module';


@NgModule({
    imports: [
        AlbumsRoutingModule,
        DefaultModule
    ]
})
export class AlbumsModule { }
