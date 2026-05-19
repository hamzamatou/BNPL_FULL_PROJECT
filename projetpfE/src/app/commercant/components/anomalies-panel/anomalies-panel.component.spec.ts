import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnomaliesPanelComponent } from './anomalies-panel.component';

describe('AnomaliesPanelComponent', () => {
  let component: AnomaliesPanelComponent;
  let fixture: ComponentFixture<AnomaliesPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnomaliesPanelComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AnomaliesPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
