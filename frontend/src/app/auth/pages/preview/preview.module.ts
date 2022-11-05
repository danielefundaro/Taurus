import { NgModule } from '@angular/core';

import { PreviewRoutingModule } from './preview-routing.module';
import { DefaultModule } from '../default/default.module';


@NgModule({
    imports: [
        PreviewRoutingModule,
        DefaultModule
    ]
})
export class PreviewModule { }
