import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HttpHeaders } from '@angular/common/http';

export interface User {
  id?: number;
  nom: string;
  prenom?: string;
  email: string;
  role: string;
  status?: 'CREATED' | 'ACTIVE' | 'BLOCKED';
  password?: string;
}
export interface Banque {
  id: number;
  nomBanque: string;
  codeBanque?: string;
  email?: string;
  telephone?: string;
  adresse?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {

  private apiUrl = "http://localhost:8080/api";

  constructor(private http: HttpClient) {}

  private getHeaders() {
    const token = localStorage.getItem('token');

    return new HttpHeaders({
      Authorization: `Bearer ${token || ''}`,
      'Content-Type': 'application/json'
    });
  }

  // ================= USERS =================

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiUrl}/users`, {
      headers: this.getHeaders()
    });
  }

  addUser(user: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/users/register`, user, {
      headers: this.getHeaders()
    });
  }

  updateUser(user: any) {
    return this.http.put(`${this.apiUrl}/users/${user.id}`, user, {
      headers: this.getHeaders()
    });
  }

  deleteUser(id: number) {
    return this.http.delete(`${this.apiUrl}/users/${id}`, {
      headers: this.getHeaders()
    });
  }

toggleBlockUser(id: number): Observable<User> {
  return this.http.put<User>(`${this.apiUrl}/users/toggle/${id}`, {}, {
    headers: this.getHeaders()
  });
}

  // ================= BANQUES =================

  getBanques(): Observable<Banque[]> {
  return this.http.get<Banque[]>(`${this.apiUrl}/banques`, {
    headers: this.getHeaders()
  });
}

  createBanque(data: any) {
    return this.http.post(`${this.apiUrl}/banques`, data, {
      headers: this.getHeaders()
    });
  }

  // ================= ANALYSTE =================

  createAnalyste(data: any) {
    return this.http.post(`${this.apiUrl}/analystes`, data, {
      headers: this.getHeaders()
    });
  }

activateAccount(data: any): Observable<any> {
  return this.http.post(`${this.apiUrl}/users/activate`, data, {
    headers: this.getHeaders()
  });
}
}