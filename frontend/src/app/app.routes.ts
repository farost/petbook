import { Routes } from '@angular/router';
import { Landing } from './pages/landing/landing';
import { Signin } from './pages/signin/signin';
import { Signup } from './pages/signup/signup';
import { Profile } from './pages/profile/profile';
import { PetPage } from './pages/pet/pet';
import { Feed } from './pages/feed/feed';
import { DiscoverPage } from './pages/discover/discover';
import { UserPage } from './pages/user/user';
import { OrganizationPage } from './pages/organization/organization';

export const routes: Routes = [
  { path: '', component: Landing },
  { path: 'signin', component: Signin },
  { path: 'signup', component: Signup },
  { path: 'profile', component: Profile },
  { path: 'pet/:id', component: PetPage },
  { path: 'user/:id', component: UserPage },
  { path: 'organization/:id', component: OrganizationPage },
  { path: 'feed', component: Feed },
  { path: 'discover', component: DiscoverPage },
  { path: '**', redirectTo: '' }
];
