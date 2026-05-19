import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RecommandationsModalComponent } from './recommandations-modal.component';

describe('RecommandationsModalComponent', () => {
  let component: RecommandationsModalComponent;
  let fixture: ComponentFixture<RecommandationsModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecommandationsModalComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(RecommandationsModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
