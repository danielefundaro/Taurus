import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AlbumEditComponent } from './album-edit.component';

const routes: Routes = [{ path: '', component: AlbumEditComponent }];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class AlbumEditRoutingModule { }
