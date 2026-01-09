import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TitleCasePipe } from '@angular/common';
import { AuthService } from '../../services/auth.service';
import { ApiService, UserProfileWithPets, PetSummary, Post, OrganizationWithRole } from '../../services/api.service';
import { PostItemComponent } from '../../components/post-item/post-item';
import { PetCardComponent } from '../../components/pet-card/pet-card';

@Component({
  selector: 'app-profile',
  imports: [RouterLink, FormsModule, TitleCasePipe, PostItemComponent, PetCardComponent],
  templateUrl: './profile.html',
  styleUrl: './profile.scss'
})
export class Profile implements OnInit {
  private authService = inject(AuthService);
  private apiService = inject(ApiService);
  private router = inject(Router);

  profile = signal<UserProfileWithPets | null>(null);
  posts = signal<Post[]>([]);
  organizations = signal<OrganizationWithRole[]>([]);
  isLoading = signal(true);
  error = signal('');

  // Profile editing
  showEditProfile = signal(false);
  editName = '';
  editBio = '';
  editLocation = '';
  editBirthYear: number | null = null;
  editBirthMonth: number | null = null;
  editBirthDay: number | null = null;
  editProfileError = '';
  isSavingProfile = false;

  // Add pet form
  showAddPet = signal(false);
  newPetName = '';
  newPetSpecies = 'dog';
  newPetBreed = '';
  newPetBio = '';
  newPetSex = '';
  newPetBirthYear: number | null = null;
  newPetBirthMonth: number | null = null;
  newPetBirthDay: number | null = null;
  newPetStatus = 'owned';
  addPetError = '';
  isAddingPet = false;

  speciesOptions = ['dog', 'cat', 'bird', 'reptile', 'small_animal', 'other'];
  sexOptions = ['', 'male', 'female', 'unknown'];
  statusOptions = ['owned', 'for_adoption', 'for_sale', 'needs_help'];
  monthOptions = [
    { value: null, label: 'Unknown' },
    { value: 1, label: 'January' },
    { value: 2, label: 'February' },
    { value: 3, label: 'March' },
    { value: 4, label: 'April' },
    { value: 5, label: 'May' },
    { value: 6, label: 'June' },
    { value: 7, label: 'July' },
    { value: 8, label: 'August' },
    { value: 9, label: 'September' },
    { value: 10, label: 'October' },
    { value: 11, label: 'November' },
    { value: 12, label: 'December' }
  ];

  // Add organization form
  showAddOrg = signal(false);
  newOrgName = '';
  newOrgType = 'shelter';
  newOrgBio = '';
  newOrgLocation = '';
  addOrgError = '';
  isAddingOrg = false;

  orgTypeOptions = ['shelter', 'rescue', 'breeder', 'vet_clinic'];

  // Create post form
  newPostContent = '';
  newPostImageUrl = '';
  isPosting = false;
  postError = '';

  // "Posting as" selector
  postAsOrgId = '';  // Empty means post as self
  showPostAsSelector = signal(false);

  async ngOnInit() {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/signin']);
      return;
    }

    await this.loadProfile();
  }

  async loadProfile() {
    this.isLoading.set(true);
    this.error.set('');

    try {
      const data = await this.apiService.getMyProfile();
      this.profile.set(data);

      // Load organizations first, then posts (need org list to fetch org posts)
      await this.loadOrganizations();
      await this.loadPosts(data.user.id);
    } catch (err: any) {
      this.error.set(err.error?.error || 'Failed to load profile');
    } finally {
      this.isLoading.set(false);
    }
  }

  toggleEditProfile() {
    this.showEditProfile.update(v => !v);
    this.editProfileError = '';
    if (this.showEditProfile()) {
      // Populate edit fields with current values
      const user = this.profile()?.user;
      this.editName = user?.name || '';
      this.editBio = user?.bio || '';
      this.editLocation = user?.location || '';
      this.editBirthYear = user?.birthYear || null;
      this.editBirthMonth = user?.birthMonth || null;
      this.editBirthDay = user?.birthDay || null;
    }
  }

  async saveProfile() {
    if (this.isSavingProfile) return;

    if (!this.editName.trim()) {
      this.editProfileError = 'Name is required';
      return;
    }

    this.isSavingProfile = true;
    this.editProfileError = '';

    try {
      // Check if birthday fields were cleared
      const currentUser = this.profile()?.user;
      const hadBirthYear = currentUser?.birthYear !== undefined && currentUser?.birthYear !== null;
      const hadBirthMonth = currentUser?.birthMonth !== undefined && currentUser?.birthMonth !== null;
      const hadBirthDay = currentUser?.birthDay !== undefined && currentUser?.birthDay !== null;

      const nowHasBirthYear = this.editBirthYear !== null;
      const nowHasBirthMonth = this.editBirthMonth !== null;
      const nowHasBirthDay = this.editBirthDay !== null;

      // Clear entire birthday if year was cleared
      const clearBirthday = hadBirthYear && !nowHasBirthYear;
      // Clear month if it had a value and now doesn't (and year is still set)
      const clearBirthMonth = hadBirthMonth && !nowHasBirthMonth && nowHasBirthYear;
      // Clear day if it had a value and now doesn't (and year is still set)
      const clearBirthDay = hadBirthDay && !nowHasBirthDay && nowHasBirthYear;

      await this.apiService.updateProfile({
        name: this.editName.trim(),
        bio: this.editBio.trim() || undefined,
        location: this.editLocation.trim() || undefined,
        birthYear: this.editBirthYear || undefined,
        birthMonth: this.editBirthMonth || undefined,
        birthDay: this.editBirthDay || undefined,
        clearBirthday: clearBirthday,
        clearBirthMonth: clearBirthMonth,
        clearBirthDay: clearBirthDay
      });

      this.showEditProfile.set(false);
      await this.loadProfile();
    } catch (err: any) {
      this.editProfileError = err.error?.error || 'Failed to update profile';
    } finally {
      this.isSavingProfile = false;
    }
  }

  private async loadPosts(userId: string) {
    try {
      // Load user's own posts
      const userPostsData = await this.apiService.getPostsByUser(userId);
      let allPosts = [...userPostsData.posts];

      // Also load posts from organizations the user manages
      const orgs = this.organizations();
      for (const orgWithRole of orgs) {
        try {
          const orgFeed = await this.apiService.getOrganizationFeed(orgWithRole.organization.id);
          // Filter to only org-authored posts (not posts by members)
          const orgPosts = orgFeed.posts.filter(p => p.authorType === 'organization');
          allPosts = [...allPosts, ...orgPosts];
        } catch {
          // Skip orgs we can't fetch feed for
        }
      }

      // Remove duplicates (in case any post appears in both) and sort by date
      const uniquePosts = allPosts.filter((post, index, self) =>
        index === self.findIndex(p => p.id === post.id)
      );
      uniquePosts.sort((a, b) => {
        const dateA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const dateB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return dateB - dateA;
      });

      this.posts.set(uniquePosts);
    } catch (err) {
      console.error('Failed to load posts:', err);
      // Don't set error - just leave posts empty
    }
  }

  private async loadOrganizations() {
    try {
      const orgsData = await this.apiService.getMyOrganizations();
      this.organizations.set(orgsData);
    } catch (err) {
      console.error('Failed to load organizations:', err);
      // Don't set error - just leave organizations empty
    }
  }

  toggleAddPet() {
    this.showAddPet.update(v => !v);
    this.addPetError = '';
    this.newPetName = '';
    this.newPetSpecies = 'dog';
    this.newPetBreed = '';
    this.newPetBio = '';
    this.newPetSex = '';
    this.newPetBirthYear = null;
    this.newPetBirthMonth = null;
    this.newPetBirthDay = null;
    this.newPetStatus = 'owned';
  }

  async addPet() {
    // Prevent double submission
    if (this.isAddingPet) {
      return;
    }

    if (!this.newPetName.trim()) {
      this.addPetError = 'Pet name is required';
      return;
    }

    this.isAddingPet = true;
    this.addPetError = '';

    try {
      await this.apiService.createPet({
        name: this.newPetName,
        species: this.newPetSpecies,
        breed: this.newPetBreed || undefined,
        bio: this.newPetBio || undefined,
        sex: this.newPetSex || undefined,
        birthYear: this.newPetBirthYear || undefined,
        birthMonth: this.newPetBirthMonth || undefined,
        birthDay: this.newPetBirthDay || undefined,
        petStatus: this.newPetStatus || undefined
      });

      this.showAddPet.set(false);
      await this.loadProfile();
    } catch (err: any) {
      this.addPetError = err.error?.error || 'Failed to add pet';
    } finally {
      this.isAddingPet = false;
    }
  }

  getSexLabel(sex: string): string {
    const labels: Record<string, string> = {
      '': 'Not specified',
      'male': 'Male',
      'female': 'Female',
      'unknown': 'Unknown'
    };
    return labels[sex] || sex;
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      'owned': 'Owned (regular pet)',
      'for_adoption': 'For Adoption',
      'for_sale': 'For Sale',
      'needs_help': 'Needs Help'
    };
    return labels[status] || status;
  }

  getDayOptions(): number[] {
    return Array.from({ length: 31 }, (_, i) => i + 1);
  }

  getYearOptions(): number[] {
    const currentYear = new Date().getFullYear();
    return Array.from({ length: 100 }, (_, i) => currentYear - i);
  }

  getPetYearOptions(): number[] {
    const currentYear = new Date().getFullYear();
    return Array.from({ length: 30 }, (_, i) => currentYear - i);
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

  getSpeciesLabel(species: string): string {
    const labels: Record<string, string> = {
      'dog': 'Dog',
      'cat': 'Cat',
      'bird': 'Bird',
      'reptile': 'Reptile',
      'small_animal': 'Small Animal',
      'other': 'Other'
    };
    return labels[species] || species;
  }

  calculateAge(birthYear?: number, birthMonth?: number, birthDay?: number): string | null {
    if (!birthYear) return null;

    const now = new Date();
    const currentYear = now.getFullYear();
    const currentMonth = now.getMonth() + 1;
    const currentDay = now.getDate();

    let years = currentYear - birthYear;
    let months = 0;

    if (birthMonth) {
      if (currentMonth < birthMonth || (currentMonth === birthMonth && birthDay && currentDay < birthDay)) {
        years--;
        months = 12 - birthMonth + currentMonth;
      } else {
        months = currentMonth - birthMonth;
      }
    }

    if (years === 0) {
      if (months === 0) return 'Less than a month';
      return `${months} month${months !== 1 ? 's' : ''}`;
    } else if (years < 2 && birthMonth) {
      return `${years} year${years !== 1 ? 's' : ''}, ${months} month${months !== 1 ? 's' : ''}`;
    } else {
      return `${years} year${years !== 1 ? 's' : ''} old`;
    }
  }

  getSexEmoji(sex?: string): string {
    if (sex === 'male') return '♂️';
    if (sex === 'female') return '♀️';
    return '';
  }

  formatUserBirthday(birthYear?: number, birthMonth?: number, birthDay?: number): string | null {
    if (!birthYear) return null;

    const monthNames = ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'];

    if (birthMonth && birthDay) {
      return `${monthNames[birthMonth - 1]} ${birthDay}, ${birthYear}`;
    } else if (birthMonth) {
      return `${monthNames[birthMonth - 1]} ${birthYear}`;
    } else {
      return `${birthYear}`;
    }
  }

  toggleAddOrg() {
    this.showAddOrg.update(v => !v);
    this.addOrgError = '';
    this.newOrgName = '';
    this.newOrgType = 'shelter';
    this.newOrgBio = '';
    this.newOrgLocation = '';
  }

  async addOrganization() {
    if (this.isAddingOrg) return;

    if (!this.newOrgName.trim()) {
      this.addOrgError = 'Organization name is required';
      return;
    }

    this.isAddingOrg = true;
    this.addOrgError = '';

    try {
      await this.apiService.createOrganization({
        name: this.newOrgName,
        orgType: this.newOrgType,
        bio: this.newOrgBio || undefined,
        location: this.newOrgLocation || undefined
      });

      this.showAddOrg.set(false);
      await this.loadProfile();
    } catch (err: any) {
      this.addOrgError = err.error?.error || 'Failed to create organization';
    } finally {
      this.isAddingOrg = false;
    }
  }

  getOrgTypeLabel(orgType: string): string {
    const labels: Record<string, string> = {
      'shelter': 'Shelter',
      'rescue': 'Rescue',
      'breeder': 'Breeder',
      'vet_clinic': 'Vet Clinic'
    };
    return labels[orgType] || orgType;
  }

  // "Posting as" selector methods
  togglePostAsSelector() {
    this.showPostAsSelector.update(v => !v);
  }

  selectPostAs(orgId: string) {
    this.postAsOrgId = orgId;
    this.showPostAsSelector.set(false);
  }

  getSelectedPostAsName(): string {
    if (!this.postAsOrgId) {
      return this.profile()?.user.name || 'Me';
    }
    const org = this.organizations().find(o => o.organization.id === this.postAsOrgId);
    return org?.organization.name || 'Unknown';
  }

  getSelectedPostAsInitial(): string {
    return this.getSelectedPostAsName().charAt(0).toUpperCase();
  }

  isPostingAsOrg(): boolean {
    return !!this.postAsOrgId;
  }

  async createPost() {
    if (!this.newPostContent.trim()) {
      this.postError = 'Post content is required';
      return;
    }

    if (this.isPosting) return;

    this.isPosting = true;
    this.postError = '';

    try {
      const userId = this.profile()?.user.id;
      // Posts from profile page always go to the user's own wall (targetUserId)
      // The author can be the user themselves or an org they manage (actingAsOrgId)
      await this.apiService.createPost({
        content: this.newPostContent,
        actingAsOrgId: this.postAsOrgId || undefined,
        targetUserId: userId,  // Post goes to MY wall
        imageUrl: this.newPostImageUrl.trim() || undefined
      });

      this.newPostContent = '';
      this.newPostImageUrl = '';
      await this.loadProfile();
    } catch (err: any) {
      this.postError = err.error?.error || 'Failed to create post';
    } finally {
      this.isPosting = false;
    }
  }

  async deletePost(postId: string) {
    if (!confirm('Delete this post?')) return;

    try {
      await this.apiService.deletePost(postId);
      await this.loadProfile();
    } catch (err: any) {
      alert(err.error?.error || 'Failed to delete post');
    }
  }

  formatTime(isoString: string): string {
    const date = new Date(isoString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString();
  }
}
