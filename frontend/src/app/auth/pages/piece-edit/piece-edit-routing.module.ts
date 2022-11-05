import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PieceEditComponent } from './piece-edit.component';

const routes: Routes = [{ path: '', component: PieceEditComponent }];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class PieceEditRoutingModule { }
