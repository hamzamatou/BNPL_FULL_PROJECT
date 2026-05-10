import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConsentementComponent } from './consentement.component';

describe('ConsentementComponent', () => {
  let component: ConsentementComponent;
  let fixture: ComponentFixture<ConsentementComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConsentementComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConsentementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
