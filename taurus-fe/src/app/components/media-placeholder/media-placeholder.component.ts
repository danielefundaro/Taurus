import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

export type MediaPlaceholderStatus = 'loading' | 'error';

@Component({
    selector: 'app-media-placeholder',
    standalone: true,
    templateUrl: './media-placeholder.component.html',
    styleUrl: './media-placeholder.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class MediaPlaceholderComponent {
    @Input() status: MediaPlaceholderStatus = 'loading';
    @Input() page = 1;
    @Output() retry = new EventEmitter<void>();
}
