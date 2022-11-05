import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { InstrumentEditComponent } from './instrument-edit.component';

const routes: Routes = [{ path: '', component: InstrumentEditComponent } ];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class InstrumentEditRoutingModule { }
