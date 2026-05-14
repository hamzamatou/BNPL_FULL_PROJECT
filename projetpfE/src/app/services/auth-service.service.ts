import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private baseUrl = 'http://localhost:8080/api/users';
  private _pendingEmail: string | null = null;

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/login`, { email, password }).pipe(
      tap(res => {
        if (res.otpRequired) {
          this._pendingEmail = email;  // stocker email temporairement
        }
      })
    );
  }

  verifyOtp(email: string, otpCode: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/verify-otp`, { email, otpCode }).pipe(
      tap(res => {
        if (res.token) {
          localStorage.setItem('token', res.token);
        }
      })
    );
  }

  resendOtp(email: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/resend-otp`, { email });
  }

  getPendingEmail(): string | null {
    return this._pendingEmail;
  }

  clearPendingEmail() {
    this._pendingEmail = null;
  }

  logout() {
    localStorage.removeItem('token');
    this._pendingEmail = null;
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getRole(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.role || null;
    } catch { return null; }
  }
}