import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, finalize, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private baseUrl = 'http://localhost:8080/api/users';
  private readonly pendingEmailKey = 'pendingOtpEmail';
  private _pendingEmail: string | null = null;

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/login`, { email, password }).pipe(
      tap(res => {
        if (res.otpRequired) {
          this.setPendingEmail(email);
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
    if (this._pendingEmail) {
      return this._pendingEmail;
    }
    const stored = sessionStorage.getItem(this.pendingEmailKey);
    this._pendingEmail = stored;
    return stored;
  }

  clearPendingEmail() {
    this._pendingEmail = null;
    sessionStorage.removeItem(this.pendingEmailKey);
  }

  private setPendingEmail(email: string) {
    this._pendingEmail = email;
    sessionStorage.setItem(this.pendingEmailKey, email);
  }

  logout(): Observable<void> {
    const token = this.getToken();
    if (!token) {
      this.clearSession();
      return of(void 0);
    }
    return this.http.post<void>(`${this.baseUrl}/logout`, {}, {
      headers: { Authorization: `Bearer ${token}` }
    }).pipe(
      finalize(() => this.clearSession())
    );
  }

  private clearSession(): void {
    localStorage.removeItem('token');
    this._pendingEmail = null;
    sessionStorage.removeItem(this.pendingEmailKey);
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

  getUserId(): number | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const id = payload?.id;
      if (id == null) return null;
      const n = Number(id);
      return Number.isFinite(n) ? n : null;
    } catch {
      return null;
    }
  }
}