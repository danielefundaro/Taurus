import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ButtonsActionsComponent } from './buttons-actions.component';

describe('ButtonsActionsComponent', () => {
  let component: ButtonsActionsComponent;
  let fixture: ComponentFixture<ButtonsActionsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ButtonsActionsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ButtonsActionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
