import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TitleCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, PetWithOwner, Post, OwnershipRecord, UserSearchResult, OrganizationWithRole } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { PostItemComponent } from '../../components/post-item/post-item';

@Component({
  selector: 'app-pet',
  imports: [RouterLink, TitleCasePipe, FormsModule, PostItemComponent],
  templateUrl: './pet.html',
  styleUrl: './pet.scss'
})
export class PetPage implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private apiService = inject(ApiService);
  authService = inject(AuthService);

  petData = signal<PetWithOwner | null>(null);
  posts = signal<Post[]>([]);
  ownershipHistory = signal<OwnershipRecord[]>([]);
  isLoading = signal(true);
  error = signal('');
  isOwner = signal(false);
  isFollowing = signal(false);
  followerCount = signal(0);

  // Post form
  newPostContent = '';
  newPostImageUrl = '';
  isPosting = false;
  postAsOrgId = '';  // Empty means post as self
  showPostAsSelector = signal(false);
  myOrganizations = signal<OrganizationWithRole[]>([]);

  // Edit pet form
  showEditPet = signal(false);
  editPetName = '';
  editPetSpecies = '';
  editPetBreed = '';
  editPetBio = '';
  editPetSex = '';
  editPetBirthYear: number | null = null;
  editPetBirthMonth: number | null = null;
  editPetBirthDay: number | null = null;
  editPetStatus = '';
  editPetError = '';
  isSavingPet = false;

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

  // Transfer form
  showTransfer = signal(false);
  transferToType = 'user';
  transferToUserId = '';
  transferToOrgId = '';
  transferReason = 'adoption';
  isTransferring = false;
  transferError = '';

  // Available users and orgs for transfer
  availableUsers = signal<UserSearchResult[]>([]);
  availableOrgs = signal<OrganizationWithRole[]>([]);

  transferReasons = ['adoption', 'surrender', 'rescue', 'sale', 'gift'];

  async ngOnInit() {
    const petId = this.route.snapshot.paramMap.get('id');
    if (!petId) {
      this.router.navigate(['/']);
      return;
    }

    await this.loadPet(petId);
  }

  async loadPet(petId: string) {
    this.isLoading.set(true);
    this.error.set('');

    try {
      const [data, postsData, historyData] = await Promise.all([
        this.apiService.getPet(petId),
        this.apiService.getPostsForPet(petId),
        this.apiService.getPetOwnershipHistory(petId)
      ]);

      this.petData.set(data);
      this.posts.set(postsData.posts);
      this.ownershipHistory.set(historyData.history);

      // Check if current user is the owner (individual or org they manage)
      const currentUser = this.authService.user();
      let isCurrentOwner = currentUser?.id === data.ownerId;

      // If owner is an org, check if current user manages it
      if (!isCurrentOwner && data.ownerType === 'organization' && currentUser) {
        try {
          const myOrgs = await this.apiService.getMyOrganizations();
          isCurrentOwner = myOrgs.some(o => o.organization.id === data.ownerId);
          this.availableOrgs.set(myOrgs);
        } catch {}
      }

      this.isOwner.set(isCurrentOwner);

      // Check follow status and load orgs if logged in
      if (this.authService.isLoggedIn()) {
        try {
          const status = await this.apiService.getPetFollowStatus(petId);
          this.isFollowing.set(status.isFollowing);
          this.followerCount.set(status.followerCount);
        } catch {}

        // Load user's organizations for post-as selector
        try {
          const myOrgs = await this.apiService.getMyOrganizations();
          this.myOrganizations.set(myOrgs);
        } catch {}
      }
    } catch (err: any) {
      this.error.set(err.error?.error || 'Failed to load pet');
    } finally {
      this.isLoading.set(false);
    }
  }

  async toggleFollow() {
    const petId = this.petData()?.pet.id;
    if (!petId) return;

    try {
      if (this.isFollowing()) {
        const status = await this.apiService.unfollowPet(petId);
        this.isFollowing.set(false);
        this.followerCount.set(status.followerCount);
      } else {
        const status = await this.apiService.followPet(petId);
        this.isFollowing.set(true);
        this.followerCount.set(status.followerCount);
      }
    } catch (err: any) {
      alert(err.error?.error || 'Failed to update follow status');
    }
  }

  async createPost() {
    const petId = this.petData()?.pet.id;
    if (!petId || !this.newPostContent.trim() || this.isPosting) return;

    this.isPosting = true;
    try {
      const newPost = await this.apiService.createPost({
        content: this.newPostContent,
        petId: petId,
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
    const org = this.myOrganizations().find(o => o.organization.id === this.postAsOrgId);
    return org?.organization.name || 'Unknown';
  }

  getSelectedPostAsInitial(): string {
    return this.getSelectedPostAsName().charAt(0).toUpperCase();
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

  async deletePet() {
    if (!confirm('Are you sure you want to remove this pet?')) {
      return;
    }

    const petId = this.petData()?.pet.id;
    if (!petId) return;

    try {
      await this.apiService.deletePet(petId);
      this.router.navigate(['/profile']);
    } catch (err: any) {
      this.error.set(err.error?.error || 'Failed to delete pet');
    }
  }

  toggleEditPet() {
    this.showEditPet.update(v => !v);
    this.editPetError = '';
    if (this.showEditPet()) {
      // Populate edit fields with current values
      const pet = this.petData()?.pet;
      this.editPetName = pet?.name || '';
      this.editPetSpecies = pet?.species || 'dog';
      this.editPetBreed = pet?.breed || '';
      this.editPetBio = pet?.bio || '';
      this.editPetSex = pet?.sex || '';
      this.editPetBirthYear = pet?.birthYear || null;
      this.editPetBirthMonth = pet?.birthMonth || null;
      this.editPetBirthDay = pet?.birthDay || null;
      this.editPetStatus = pet?.petStatus || 'owned';
    }
  }

  async savePet() {
    if (this.isSavingPet) return;

    const petId = this.petData()?.pet.id;
    if (!petId) return;

    if (!this.editPetName.trim()) {
      this.editPetError = 'Pet name is required';
      return;
    }

    this.isSavingPet = true;
    this.editPetError = '';

    try {
      await this.apiService.updatePet(petId, {
        name: this.editPetName.trim(),
        species: this.editPetSpecies,
        breed: this.editPetBreed.trim() || undefined,
        bio: this.editPetBio.trim() || undefined,
        sex: this.editPetSex || undefined,
        birthYear: this.editPetBirthYear || undefined,
        birthMonth: this.editPetBirthMonth || undefined,
        birthDay: this.editPetBirthDay || undefined,
        petStatus: this.editPetStatus || undefined
      });

      this.showEditPet.set(false);
      await this.loadPet(petId);
    } catch (err: any) {
      this.editPetError = err.error?.error || 'Failed to update pet';
    } finally {
      this.isSavingPet = false;
    }
  }

  getDayOptions(): number[] {
    return Array.from({ length: 31 }, (_, i) => i + 1);
  }

  getYearOptions(): number[] {
    const currentYear = new Date().getFullYear();
    return Array.from({ length: 30 }, (_, i) => currentYear - i);
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

  isMyPost(post: Post): boolean {
    // Check if the post was authored by the current user
    if (post.authorId === this.authService.user()?.id) {
      return true;
    }
    // Check if the post was authored by one of the user's organizations
    if (post.authorType === 'organization') {
      return this.myOrganizations().some(o => o.organization.id === post.authorId);
    }
    return false;
  }

  async deletePost(postId: string) {
    if (!confirm('Delete this post?')) return;

    const petId = this.petData()?.pet.id;
    if (!petId) return;

    try {
      await this.apiService.deletePost(postId);
      await this.loadPet(petId);
    } catch (err: any) {
      alert(err.error?.error || 'Failed to delete post');
    }
  }

  async toggleTransfer() {
    this.showTransfer.update(v => !v);
    this.transferError = '';
    this.transferToUserId = '';
    this.transferToOrgId = '';
    this.transferReason = 'adoption';
    this.transferToType = 'user';

    if (this.showTransfer()) {
      // Load available users and orgs for transfer
      try {
        const currentOwnerId = this.petData()?.ownerId;
        const currentOwnerType = this.petData()?.ownerType;

        // Load users - filter out current owner only if it's an individual
        const usersResponse = await this.apiService.getAllUsers(1, 100);
        if (currentOwnerType === 'individual') {
          this.availableUsers.set(usersResponse.users.filter(u => u.id !== currentOwnerId));
        } else {
          // If org owns the pet, all users are available (including current user)
          this.availableUsers.set(usersResponse.users);
        }

        // Load organizations user manages - filter out current owner if it's an org
        const orgs = await this.apiService.getMyOrganizations();
        if (currentOwnerType === 'organization') {
          this.availableOrgs.set(orgs.filter(o => o.organization.id !== currentOwnerId));
        } else {
          this.availableOrgs.set(orgs);
        }
      } catch {}
    }
  }

  async transferPet() {
    if (this.isTransferring) return;

    const petId = this.petData()?.pet.id;
    if (!petId) return;

    // Validate
    if (this.transferToType === 'user' && !this.transferToUserId) {
      this.transferError = 'Please select a user';
      return;
    }
    if (this.transferToType === 'org' && !this.transferToOrgId) {
      this.transferError = 'Please select an organization';
      return;
    }

    this.isTransferring = true;
    this.transferError = '';

    try {
      await this.apiService.transferPet(petId, {
        toUserId: this.transferToType === 'user' ? this.transferToUserId : undefined,
        toOrgId: this.transferToType === 'org' ? this.transferToOrgId : undefined,
        reason: this.transferReason
      });

      this.showTransfer.set(false);
      await this.loadPet(petId);
      alert('Pet transferred successfully!');
    } catch (err: any) {
      this.transferError = err.error?.error || 'Failed to transfer pet';
    } finally {
      this.isTransferring = false;
    }
  }

  getReasonLabel(reason: string): string {
    const labels: Record<string, string> = {
      'adoption': 'Adoption',
      'surrender': 'Surrender',
      'rescue': 'Rescue',
      'sale': 'Sale',
      'gift': 'Gift'
    };
    return labels[reason] || reason;
  }

  formatDate(isoString?: string): string {
    if (!isoString) return 'Unknown';
    return new Date(isoString).toLocaleDateString();
  }

  // Age calculation from birth year/month/day
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
        if (birthDay && currentDay < birthDay) months--;
      } else {
        months = currentMonth - birthMonth;
        if (birthDay && currentDay < birthDay) months--;
      }
    }

    if (years === 0) {
      if (months === 0) return 'Less than 1 month';
      return months === 1 ? '1 month' : `${months} months`;
    }

    if (years === 1) {
      return months > 0 ? `1 year, ${months} month${months > 1 ? 's' : ''}` : '1 year';
    }

    return `${years} years`;
  }

  // Format birthday display with uncertainty indicator
  formatBirthday(birthYear?: number, birthMonth?: number, birthDay?: number): string | null {
    if (!birthYear) return null;

    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    if (birthDay && birthMonth) {
      return `${monthNames[birthMonth - 1]} ${birthDay}, ${birthYear}`;
    } else if (birthMonth) {
      return `${monthNames[birthMonth - 1]} ${birthYear}`;
    } else {
      return `${birthYear}`;
    }
  }

  hasBirthdayUncertainty(): boolean {
    const pet = this.petData()?.pet;
    if (!pet?.birthYear) return false;
    return !pet.birthMonth || !pet.birthDay;
  }

  getSexLabel(sex?: string): string {
    if (!sex) return '';
    const labels: Record<string, string> = {
      'male': 'Male',
      'female': 'Female',
      'unknown': 'Unknown'
    };
    return labels[sex] || sex;
  }

  getSexEmoji(sex?: string): string {
    if (!sex) return '';
    const emojis: Record<string, string> = {
      'male': '♂',
      'female': '♀',
      'unknown': '?'
    };
    return emojis[sex] || '';
  }

  getStatusLabel(status?: string): string {
    const labels: Record<string, string> = {
      'owned': 'Owned',
      'for_adoption': 'For Adoption',
      'for_sale': 'For Sale',
      'needs_help': 'Needs Help'
    };
    return labels[status || 'owned'] || status || 'Owned';
  }

  getStatusClass(status?: string): string {
    const classes: Record<string, string> = {
      'owned': 'status-owned',
      'for_adoption': 'status-adoption',
      'for_sale': 'status-sale',
      'needs_help': 'status-help'
    };
    return classes[status || 'owned'] || 'status-owned';
  }

  isSpecialStatus(): boolean {
    const status = this.petData()?.pet.petStatus;
    return status !== 'owned' && !!status;
  }
}
