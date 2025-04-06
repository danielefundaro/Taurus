import { Routes } from '@angular/router';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { DetailComponent } from './detail/detail.component';
import { TracksComponent } from './tracks.component';

export default [
    {
        path: '',
        component: TracksComponent,
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