import { NgModule } from '@angular/core';

import { NotFoundRoutingModule } from './not-found-routing.module';
import { DefaultModule } from '../default/default.module';


@NgModule({
    imports: [
        NotFoundRoutingModule,
        DefaultModule,
    ]
})
export class NotFoundModule { }
