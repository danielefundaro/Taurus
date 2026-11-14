import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { FloatingConfiguratorComponent } from '../../components/floating-configurator/floating-configurator.component';

@Component({
    selector: 'app-access',
    standalone: true,
    imports: [ButtonModule, RouterModule, RippleModule, FloatingConfiguratorComponent, ButtonModule],
    templateUrl: './forbidden.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './forbidden.component.scss'
})
export class Forbidden {}
