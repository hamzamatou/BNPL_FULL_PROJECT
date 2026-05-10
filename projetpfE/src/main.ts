import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideZoneChangeDetection, importProvidersFrom } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';

import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes),
    provideZoneChangeDetection({ eventCoalescing: true }),
    importProvidersFrom(HttpClientModule) // <-- ça fournit HttpClient à tous les services
  ]
})
.catch(err => console.error(err));