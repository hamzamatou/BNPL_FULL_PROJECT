import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AddUserComponent } from './add-user.component';
import { UserService } from '../services/user.service';

describe('AddUserComponent', () => {
  let component: AddUserComponent;
  let fixture: ComponentFixture<AddUserComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddUserComponent],
      providers: [
        {
          provide: UserService,
          useValue: {
            getBanques: () => of([]),
            addUser: () => of({}),
            createAnalyste: () => of({}),
            createBanque: () => of({ id: 1 }),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AddUserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
