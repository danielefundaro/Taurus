import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { FloatingConfiguratorComponent } from '../../components/floating-configurator/floating-configurator.component';

@Component({
    selector: 'app-notfound',
    standalone: true,
    imports: [
        RouterModule,
        FloatingConfiguratorComponent,
        ButtonModule
    ],
    templateUrl: './notfound.component.html',
    styleUrl: './notfound.component.scss',
})
export class Notfound { }
