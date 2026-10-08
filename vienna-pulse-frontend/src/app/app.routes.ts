import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Vienna Pulse',
    loadComponent: () => import('./map/pulse-map').then((m) => m.PulseMap),
  },
  { path: '**', redirectTo: '' },
];
