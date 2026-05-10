import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DonneesFinancieresComponent } from './donnees-financieres.component';

describe('DonneesFinancieresComponent', () => {
  let component: DonneesFinancieresComponent;
  let fixture: ComponentFixture<DonneesFinancieresComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DonneesFinancieresComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DonneesFinancieresComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
