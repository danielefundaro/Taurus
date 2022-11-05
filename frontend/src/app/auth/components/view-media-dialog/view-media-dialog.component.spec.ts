import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewMediaDialogComponent } from './view-media-dialog.component';

describe('ViewMediaDialogComponent', () => {
  let component: ViewMediaDialogComponent;
  let fixture: ComponentFixture<ViewMediaDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ViewMediaDialogComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewMediaDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
