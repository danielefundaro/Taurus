import { Routes } from '@angular/router';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { AlbumsComponent } from './albums.component';
import { DetailComponent } from './detail/detail.component';

export default [
    {
        path: '',
        component: AlbumsComponent,
        canActivate: [canActivateAuthRole],
        data: { role: ['ROLE_USER'] },
    },
    {
        path: ':id',
        component: DetailComponent,
        canActivate: [canActivateAuthRole],
        data: { role: ['ROLE_USER'] },
    },
] as Routes;