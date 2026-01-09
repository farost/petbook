import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, UserProfileWithPets, PetSummary } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-user',
  imports: [RouterLink],
  templateUrl: './user.html',
  styleUrl: './user.scss'
})
export class UserPage implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private apiService = inject(ApiService);
  authService = inject(AuthService);

  profile = signal<UserProfileWithPets | null>(null);
  isLoading = signal(true);
  error = signal('');
  isFollowing = signal(false);
  followerCount = signal(0);
  isOwnProfile = signal(false);

  async ngOnInit() {
    const userId = this.route.snapshot.paramMap.get('id');
    if (!userId) {
      this.router.navigate(['/']);
      return;
    }

    // Check if this is the current user's profile
    const currentUser = this.authService.user();
    if (currentUser?.id === userId) {
      this.router.navigate(['/profile']);
      return;
    }

    await this.loadProfile(userId);
  }

  async loadProfile(userId: string) {
    this.isLoading.set(true);
    this.error.set('');

    try {
      const data = await this.apiService.getUserProfile(userId);
      this.profile.set(data);

      // Check follow status if logged in
      if (this.authService.isLoggedIn()) {
        try {
          const status = await this.apiService.getUserFollowStatus(userId);
          this.isFollowing.set(status.isFollowing);
          this.followerCount.set(status.followerCount);
        } catch {}
      }
    } catch (err: any) {
      this.error.set(err.error?.error || 'Failed to load profile');
    } finally {
      this.isLoading.set(false);
    }
  }

  async toggleFollow() {
    const userId = this.profile()?.user.id;
    if (!userId) return;

    try {
      if (this.isFollowing()) {
        const status = await this.apiService.unfollowUser(userId);
        this.isFollowing.set(false);
        this.followerCount.set(status.followerCount);
      } else {
        const status = await this.apiService.followUser(userId);
        this.isFollowing.set(true);
        this.followerCount.set(status.followerCount);
      }
    } catch (err: any) {
      alert(err.error?.error || 'Failed to update follow status');
    }
  }

  getSpeciesEmoji(species: string): string {
    const emojis: Record<string, string> = {
      'dog': '🐕',
      'cat': '🐈',
      'bird': '🐦',
      'reptile': '🦎',
      'small_animal': '🐹',
      'other': '🐾'
    };
    return emojis[species] || '🐾';
  }
}
