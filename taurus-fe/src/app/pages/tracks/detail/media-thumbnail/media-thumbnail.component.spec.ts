import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { MediaService } from '../../../../service';
import { MediaThumbnailComponent } from './media-thumbnail.component';

describe('MediaThumbnailComponent', () => {
    let fixture: ComponentFixture<MediaThumbnailComponent>;
    let component: MediaThumbnailComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MediaThumbnailComponent],
            providers: [provideNoopAnimations(), { provide: MediaService, useValue: { streamImage: () => of(new Blob()) } }]
        }).compileComponents();

        fixture = TestBed.createComponent(MediaThumbnailComponent);
        component = fixture.componentInstance;
        component.media = { index: 10 };
        component.alt = 'Pagina 1';
        fixture.detectChanges();
    });

    afterEach(() => {
        component['previewImage']?.closePreview();
        fixture.detectChanges();
        fixture.destroy();
    });

    it('opens the PrimeNG preview through its native trigger', () => {
        component['imageUrl'] = 'blob:page-preview';
        component['loading'] = false;
        component['changeDetector'].detectChanges();
        const trigger = component['previewImage']?.previewButton?.nativeElement as HTMLButtonElement;
        spyOn(trigger, 'click').and.callThrough();

        component.openPreview();
        component['changeDetector'].detectChanges();

        expect(trigger.click).toHaveBeenCalled();
        expect(document.body.querySelector('.p-image-mask')).not.toBeNull();
    });
});
