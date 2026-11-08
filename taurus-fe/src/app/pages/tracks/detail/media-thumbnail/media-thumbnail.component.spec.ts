import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MediaService } from '../../../../service';
import { MediaThumbnailComponent } from './media-thumbnail.component';

describe('MediaThumbnailComponent', () => {
    let fixture: ComponentFixture<MediaThumbnailComponent>;
    let component: MediaThumbnailComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MediaThumbnailComponent],
            providers: [{ provide: MediaService, useValue: { streamImage: () => of(new Blob()) } }]
        }).compileComponents();

        fixture = TestBed.createComponent(MediaThumbnailComponent);
        component = fixture.componentInstance;
        component.media = { index: 10 };
        component.alt = 'Pagina 1';
        fixture.detectChanges();
    });

    // PrimeNG sposta la maschera dell'anteprima nel body e la toglie solo a fine animazione: distruggere il
    // componente prima la lascerebbe nel documento, dove blocca le spec che cercano una maschera aperta. La
    // chiusura passa dal pulsante, perché il componente è OnPush e `closePreview()` da solo non aggiorna la vista.
    afterEach(async () => {
        (component['previewImage']?.closeButton?.nativeElement as HTMLButtonElement | undefined)?.click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        fixture.destroy();
        expect(document.body.querySelector('.p-image-mask')).toBeNull();
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
