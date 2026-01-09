import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TitleCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, OrganizationWithPets, Post, OrgPetSummary, UserSearchResult } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { PostItemComponent } from '../../components/post-item/post-item';
import { PetCardComponent } from '../../components/pet-card/pet-card';

@Component({
  selector: 'app-organization',
  imports: [RouterLink, TitleCasePipe, FormsModule, PostItemComponent, PetCardComponent],
  templateUrl: './organization.html',
  styleUrl: './organization.scss'
})
export class OrganizationPage implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private apiService = inject(ApiService);
  authService = inject(AuthService);

  orgData = signal<OrganizationWithPets | null>(null);
  posts = signal<Post[]>([]);
  isLoading = signal(true);
  error = signal('');
  isManager = signal(false);
  isOwner = signal(false);
  isFollowing = signal(false);
  followerCount = signal(0);

  // Edit organization form
  showEditOrg = signal(false);
  editOrgName = '';
  editOrgBio = '';
  editOrgLocation = '';
  editEstablishedYear: number | null = null;
  editEstablishedMonth: number | null = null;
  editEstablishedDay: number | null = null;
  editOrgError = '';
  isSavingOrg = false;

  // Transfer organization form
  showTransferOrg = signal(false);
  transferSearch = '';
  transferSearchResults = signal<UserSearchResult[]>([]);
  selectedTransferUser: UserSearchResult | null = null;
  transferError = '';
  isTransferring = false;
  isSearchingUsers = false;

  // Post form
  newPostContent = '';
  newPostImageUrl = '';
  isPosting = false;
  postAsOrgId = '';  // Empty means post as self, non-empty means post as org
  showPostAsSelector = signal(false);

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

  async ngOnInit() {
    const orgId = this.route.snapshot.paramMap.get('id');
    if (!orgId) {
      this.router.navigate(['/']);
      return;
    }

    await this.loadOrganization(orgId);
  }

  async loadOrganization(orgId: string) {
    this.isLoading.set(true);
    this.error.set('');

    try {
      const data = await this.apiService.getOrganization(orgId);
      this.orgData.set(data);
      this.followerCount.set(data.followerCount);

      // Check if current user manages this org
      if (this.authService.isLoggedIn()) {
        const myOrgs = await this.apiService.getMyOrganizations();
        const userOrg = myOrgs.find(o => o.organization.id === orgId);
        this.isManager.set(!!userOrg);
        this.isOwner.set(userOrg?.role === 'owner');

        // Load org feed if manager
        if (this.isManager()) {
          const feedData = await this.apiService.getOrganizationFeed(orgId);
          this.posts.set(feedData.posts);
          // Default to posting as the org
          this.postAsOrgId = orgId;
        }

        // Check follow status
        try {
          const status = await this.apiService.getOrganizationFollowStatus(orgId);
          this.isFollowing.set(status.isFollowing);
          this.followerCount.set(status.followerCount);
        } catch {}
      }
    } catch (err: any) {
      this.error.set(err.error?.error || 'Failed to load organization');
    } finally {
      this.isLoading.set(false);
    }
  }

  async toggleFollow() {
    const orgId = this.orgData()?.organization.id;
    if (!orgId) return;

    try {
      if (this.isFollowing()) {
        const status = await this.apiService.unfollowOrganization(orgId);
        this.isFollowing.set(false);
        this.followerCount.set(status.followerCount);
      } else {
        const status = await this.apiService.followOrganization(orgId);
        this.isFollowing.set(true);
        this.followerCount.set(status.followerCount);
      }
    } catch (err: any) {
      alert(err.error?.error || 'Failed to update follow status');
    }
  }

  toggleEditOrg() {
    this.showEditOrg.update(v => !v);
    this.editOrgError = '';
    if (this.showEditOrg()) {
      // Populate edit fields with current values
      const org = this.orgData()?.organization;
      this.editOrgName = org?.name || '';
      this.editOrgBio = org?.bio || '';
      this.editOrgLocation = org?.location || '';
      this.editEstablishedYear = org?.establishedYear || null;
      this.editEstablishedMonth = org?.establishedMonth || null;
      this.editEstablishedDay = org?.establishedDay || null;
    }
  }

  async saveOrg() {
    if (this.isSavingOrg) return;

    const orgId = this.orgData()?.organization.id;
    if (!orgId) return;

    if (!this.editOrgName.trim()) {
      this.editOrgError = 'Organization name is required';
      return;
    }

    this.isSavingOrg = true;
    this.editOrgError = '';

    try {
      // Check if establishment date was cleared
      const currentOrg = this.orgData()?.organization;
      const hadEstablishedDate = currentOrg?.establishedYear !== undefined && currentOrg?.establishedYear !== null;
      const nowHasEstablishedDate = this.editEstablishedYear !== null;
      const clearEstablishedDate = hadEstablishedDate && !nowHasEstablishedDate;

      await this.apiService.updateOrganization(orgId, {
        name: this.editOrgName.trim(),
        bio: this.editOrgBio.trim() || undefined,
        location: this.editOrgLocation.trim() || undefined,
        establishedYear: this.editEstablishedYear || undefined,
        establishedMonth: this.editEstablishedMonth || undefined,
        establishedDay: this.editEstablishedDay || undefined,
        clearEstablishedDate: clearEstablishedDate
      });

      this.showEditOrg.set(false);
      await this.loadOrganization(orgId);
    } catch (err: any) {
      this.editOrgError = err.error?.error || 'Failed to update organization';
    } finally {
      this.isSavingOrg = false;
    }
  }

  toggleTransferOrg() {
    this.showTransferOrg.update(v => !v);
    this.transferError = '';
    this.transferSearch = '';
    this.transferSearchResults.set([]);
    this.selectedTransferUser = null;
  }

  async searchUsersForTransfer() {
    if (!this.transferSearch.trim() || this.isSearchingUsers) return;

    this.isSearchingUsers = true;
    this.transferError = '';

    try {
      const result = await this.apiService.getAllUsers(1, 10, this.transferSearch.trim());
      // Filter out current user
      const currentUserId = this.authService.user()?.id;
      this.transferSearchResults.set(
        result.users.filter(u => u.id !== currentUserId)
      );
    } catch (err: any) {
      this.transferError = err.error?.error || 'Failed to search users';
    } finally {
      this.isSearchingUsers = false;
    }
  }

  selectTransferUser(user: UserSearchResult) {
    this.selectedTransferUser = user;
    this.transferSearchResults.set([]);
    this.transferSearch = user.name;
  }

  async transferOrg() {
    if (this.isTransferring || !this.selectedTransferUser) return;

    const orgId = this.orgData()?.organization.id;
    if (!orgId) return;

    const confirmMsg = `Are you sure you want to transfer "${this.orgData()?.organization.name}" to ${this.selectedTransferUser.name}? This action cannot be undone.`;
    if (!confirm(confirmMsg)) return;

    this.isTransferring = true;
    this.transferError = '';

    try {
      await this.apiService.transferOrganization(orgId, this.selectedTransferUser.id);
      this.showTransferOrg.set(false);
      // Redirect to home since user no longer owns this org
      alert('Organization transferred successfully!');
      this.router.navigate(['/']);
    } catch (err: any) {
      this.transferError = err.error?.error || 'Failed to transfer organization';
    } finally {
      this.isTransferring = false;
    }
  }

  async createPost() {
    const orgId = this.orgData()?.organization.id;
    if (!orgId || !this.newPostContent.trim() || this.isPosting) return;

    this.isPosting = true;
    try {
      const newPost = await this.apiService.createPost({
        content: this.newPostContent,
        actingAsOrgId: this.postAsOrgId || undefined,
        imageUrl: this.newPostImageUrl.trim() || undefined
      });
      // Add the new post to the beginning without reloading everything
      this.posts.update(posts => [newPost, ...posts]);
      this.newPostContent = '';
      this.newPostImageUrl = '';
    } catch (err: any) {
      alert(err.error?.error || 'Failed to create post');
    } finally {
      this.isPosting = false;
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
    if (this.isAddingPet) return;

    const orgId = this.orgData()?.organization.id;
    if (!orgId) return;

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
        petStatus: this.newPetStatus || undefined,
        actingAsOrgId: orgId
      });

      this.showAddPet.set(false);
      await this.loadOrganization(orgId);
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
    return Array.from({ length: 30 }, (_, i) => currentYear - i);
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

  formatEstablishedDate(establishedYear?: number, establishedMonth?: number, establishedDay?: number): string | null {
    if (!establishedYear) return null;

    const monthNames = ['January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'];

    if (establishedMonth && establishedDay) {
      return `${monthNames[establishedMonth - 1]} ${establishedDay}, ${establishedYear}`;
    } else if (establishedMonth) {
      return `${monthNames[establishedMonth - 1]} ${establishedYear}`;
    } else {
      return `${establishedYear}`;
    }
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
      return this.authService.user()?.name || 'Me';
    }
    return this.orgData()?.organization.name || 'Organization';
  }

  getSelectedPostAsInitial(): string {
    return this.getSelectedPostAsName().charAt(0).toUpperCase();
  }

  isPostingAsOrg(): boolean {
    return !!this.postAsOrgId;
  }

  canDeletePost(post: Post): boolean {
    // Can delete if you're the author or if you manage the org that authored it
    const currentUser = this.authService.user();
    if (!currentUser) return false;
    if (post.authorId === currentUser.id) return true;
    if (post.authorType === 'organization' && this.isManager()) return true;
    return false;
  }

  async deletePost(postId: string) {
    if (!confirm('Delete this post?')) return;

    const orgId = this.orgData()?.organization.id;
    if (!orgId) return;

    try {
      await this.apiService.deletePost(postId);
      await this.loadOrganization(orgId);
    } catch (err: any) {
      alert(err.error?.error || 'Failed to delete post');
    }
  }
}
