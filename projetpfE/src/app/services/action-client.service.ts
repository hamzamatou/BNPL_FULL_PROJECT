import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ActionClientService {
  private readonly baseUrl = 'http://localhost:8081/api/actions-client';
  private readonly demandesBaseUrl = 'http://localhost:8081/api/demandes';

  constructor(private readonly http: HttpClient) {}

  sendOtp(
    token: string,
    nom: string,
    prenom: string,
    cin: string,
    referenceDemande?: string
  ): Observable<void> {
    const params: Record<string, string> = { token, nom, prenom, cin };
    if (referenceDemande) {
      params['referenceDemande'] = referenceDemande;
    }
    return this.http.post<void>(`${this.baseUrl}/send-otp`, null, { params });
  }

  verifyOtp(token: string, otp: string, referenceDemande?: string): Observable<void> {
    const params: Record<string, string> = { token, otp };
    if (referenceDemande) {
      params['referenceDemande'] = referenceDemande;
    }
    return this.http.post<void>(`${this.baseUrl}/verify-otp`, null, { params });
  }

  confirmConsent(token: string): Observable<any> {
    return this.http.post(`${this.demandesBaseUrl}/consentement/confirm`, null, {
      params: { token },
    });
  }
}
