import { Routes } from '@angular/router';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { DetailComponent } from './detail/detail.component';
import { UsersComponent } from './users.component';

export default [
    {
        path: '',
        component: UsersComponent,
        canActivate: [canActivateAuthRole],
        data: { role: ['ROLE_ADMIN'] },
    },
    {
        path: ':id',
        component: DetailComponent,
        canActivate: [canActivateAuthRole],
        data: { role: ['ROLE_ADMIN'] },
    },
] as Routes;