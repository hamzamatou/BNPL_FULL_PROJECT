import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InfosClientComponent } from './infos-client.component';

describe('InfosClientComponent', () => {
  let component: InfosClientComponent;
  let fixture: ComponentFixture<InfosClientComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InfosClientComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(InfosClientComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
