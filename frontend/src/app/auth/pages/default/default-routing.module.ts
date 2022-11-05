import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from 'src/app/guard/auth.guard';
import { DefaultComponent } from './default.component';

const routes: Routes = [{
    path: '',
    component: DefaultComponent,
    canActivate: [AuthGuard],
    children: [
        { path: 'pieces', loadChildren: () => import('../pieces/pieces.module').then(m => m.PiecesModule) },
        { path: 'pieces\/:id', loadChildren: () => import('../piece-edit/piece-edit.module').then(m => m.PieceEditModule) },
        { path: 'instruments', loadChildren: () => import('../instruments/instruments.module').then(m => m.InstrumentsModule) },
        { path: 'instruments\/:id', loadChildren: () => import('../instrument-edit/instrument-edit.module').then(m => m.InstrumentEditModule) },
        { path: 'albums', loadChildren: () => import('../albums/albums.module').then(m => m.AlbumsModule) },
        { path: 'albums\/:id', loadChildren: () => import('../album-edit/album-edit.module').then(m => m.AlbumEditModule) },
        { path: 'preview', loadChildren: () => import('../preview/preview.module').then(m => m.PreviewModule) },
        { path: '404', loadChildren: () => import('../not-found/not-found.module').then(m => m.NotFoundModule) },
        { path: '**', redirectTo: '/404', pathMatch: 'full' },
    ]
}];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class DefaultRoutingModule { }
