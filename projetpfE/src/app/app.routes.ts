import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { AdminPortalComponent } from './admin/admin.component';
import { NouvelleDemandeComponent } from './commercant/pages/nouvelle-demande/nouvelle-demande.component';
import { MesDemandesComponent } from './commercant/pages/mes-demandes/mes-demandes.component';
import { DemandeDetailComponent } from './commercant/pages/demande-detail/demande-detail.component';
import { ActionClientComponent } from './client/pages/action-client/action-client.component';
import { RoleGuard } from './guards/role.guard';
import { BanquePortalComponent } from './banque/banque-portal/banque-portal.component';
import { BanqueDemandesComponent } from './banque/pages/banque-demandes/banque-demandes.component';
import { BanquePriseEnChargeComponent } from './banque/pages/banque-prise-en-charge/banque-prise-en-charge.component';
import { BanquePriseEnChargeDetailComponent } from './banque/pages/banque-prise-en-charge-detail/banque-prise-en-charge-detail.component';
import { ActivateAccountComponent } from './activate-account/activate-account.component';
import { UserDetailComponent } from './admin/user-detail/user-detail.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'activate-account/:token', component: ActivateAccountComponent },
  {
    path: 'admin',
    component: AdminPortalComponent,
    canActivate: [RoleGuard],
    data: { role: 'ADMIN' },
  },
  {
    path: 'admin/user/:id',
    component: UserDetailComponent,
    canActivate: [RoleGuard],
    data: { role: 'ADMIN' },
  },
  {
    path: 'commercant',
    component: NouvelleDemandeComponent,
    canActivate: [RoleGuard],
    data: { role: 'COMMERCANT' },
  },
  {
    path: 'mes-demandes',
    component: MesDemandesComponent,
    canActivate: [RoleGuard],
    data: { role: 'COMMERCANT' },
  },
  {
    path: 'mes-demandes/:id',
    component: DemandeDetailComponent,
    canActivate: [RoleGuard],
    data: { role: 'COMMERCANT' },
  },
  {
    path: 'action-client',
    component: ActionClientComponent,
  },
  {
    path: 'banque',
    component: BanquePortalComponent,
    canActivate: [RoleGuard],
    canActivateChild: [RoleGuard],
    data: { role: 'BANQUE' },
    children: [
      { path: 'demandes', component: BanqueDemandesComponent, data: { role: 'BANQUE' } },
      { path: 'affectees', component: BanquePriseEnChargeComponent, data: { role: 'BANQUE' } },
      { path: 'affectees/:id', component: BanquePriseEnChargeDetailComponent, data: { role: 'BANQUE' } },
      { path: '', redirectTo: 'demandes', pathMatch: 'full' },
    ],
  },
  {
    path: '**',
    redirectTo: 'login',
  },
];
