import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PieceEditComponent } from './piece-edit.component';

describe('PieceEditComponent', () => {
    let component: PieceEditComponent;
    let fixture: ComponentFixture<PieceEditComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [PieceEditComponent]
        })
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(PieceEditComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
