import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { UserService } from '../services/user.service';

@Component({
  selector: 'app-activate-account',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './activate-account.component.html',
  styleUrls: ['./activate-account.component.css']
})
export class ActivateAccountComponent {

  token: string = '';

  form = {
    email: '',
    password: '',
    confirmPassword: ''
  };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private userService: UserService
  ) {
this.token = this.route.snapshot.paramMap.get('token') || '';  }

  activate() {

  if (!this.token) {
    alert("Token manquant dans l'URL");
    return;
  }

  if (this.form.password !== this.form.confirmPassword) {
    alert("Passwords non identiques");
    return;
  }

  const body = {
    token: this.token,
    password: this.form.password,
    confirmPassword: this.form.confirmPassword
  };

  this.userService.activateAccount(body).subscribe({
    next: () => {
      alert("Compte activé !");
      this.router.navigate(['/login']);
    },
    error: (err) => {
      console.log(err);
      alert(err.error?.error || "Erreur activation");
    }
  });
}
}