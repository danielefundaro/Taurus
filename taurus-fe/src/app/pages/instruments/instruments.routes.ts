import { Routes } from '@angular/router';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { DetailComponent } from './detail/detail.component';
import { InstrumentsComponent } from './instruments.component';

export default [
    {
        path: '',
        component: InstrumentsComponent,
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