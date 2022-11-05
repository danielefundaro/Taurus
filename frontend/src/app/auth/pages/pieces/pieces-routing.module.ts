import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PiecesComponent } from './pieces.component';

const routes: Routes = [{ path: '', component: PiecesComponent }];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class PiecesRoutingModule { }
