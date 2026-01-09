import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { ApiService, Post, PetSummary, OrganizationWithRole } from '../../services/api.service';
import { PostItemComponent } from '../../components/post-item/post-item';

interface PostTarget {
  id: string;
  label: string;
  type: 'my-wall' | 'my-pet' | 'org-wall' | 'org-pet';
  orgId?: string;
  petId?: string;
  petSpecies?: string;
}

@Component({
  selector: 'app-feed',
  imports: [RouterLink, FormsModule, PostItemComponent],
  templateUrl: './feed.html',
  styleUrl: './feed.scss'
})
export class Feed implements OnInit {
  authService = inject(AuthService);
  private apiService = inject(ApiService);
  private router = inject(Router);

  posts = signal<Post[]>([]);
  myPets = signal<PetSummary[]>([]);
  myOrgs = signal<OrganizationWithRole[]>([]);
  postTargets = signal<PostTarget[]>([]);
  isLoading = signal(true);
  error = signal('');

  // New post form
  newPostContent = '';
  newPostImageUrl = '';
  selectedTargetId = '';
  isPosting = false;
  postError = '';

  // "Posting as" selector
  postAsOrgId = '';  // Empty means post as self
  showPostAsSelector = signal(false);

  // "Where to post" selector
  showTargetSelector = signal(false);

  async ngOnInit() {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/signin']);
      return;
    }

    await this.loadData();
  }

  async loadData() {
    this.isLoading.set(true);
    this.error.set('');

    try {
      const [feedData, profileData, orgsData] = await Promise.all([
        this.apiService.getFeed(),
        this.apiService.getMyProfile(),
        this.apiService.getMyOrganizations()
      ]);

      this.posts.set(feedData.posts);
      this.myPets.set(profileData.pets);
      this.myOrgs.set(orgsData);

      // Build post targets list
      await this.buildPostTargets(profileData.pets, orgsData);
    } catch (err: any) {
      this.error.set(err.error?.error || 'Failed to load feed');
    } finally {
      this.isLoading.set(false);
    }
  }

  private async buildPostTargets(myPets: PetSummary[], orgs: OrganizationWithRole[]) {
    const targets: PostTarget[] = [
      { id: 'my-wall', label: 'My wall', type: 'my-wall' }
    ];

    // Add my pets
    for (const pet of myPets) {
      targets.push({
        id: `my-pet-${pet.id}`,
        label: `${pet.name}'s wall`,
        type: 'my-pet',
        petId: pet.id,
        petSpecies: pet.species
      });
    }

    // Add orgs and their pets
    for (const orgData of orgs) {
      const org = orgData.organization;
      targets.push({
        id: `org-wall-${org.id}`,
        label: `${org.name}'s wall (${this.getOrgTypeLabel(org.orgType)})`,
        type: 'org-wall',
        orgId: org.id
      });

      // Load pets for this org
      try {
        const orgWithPets = await this.apiService.getOrganization(org.id);
        for (const pet of orgWithPets.pets) {
          targets.push({
            id: `org-pet-${org.id}-${pet.id}`,
            label: `${pet.name}'s wall (${org.name})`,
            type: 'org-pet',
            orgId: org.id,
            petId: pet.id,
            petSpecies: pet.species
          });
        }
      } catch (err) {
        console.error(`Failed to load pets for org ${org.id}:`, err);
      }
    }

    this.postTargets.set(targets);

    // Set default selection to "My wall" if not already set
    if (!this.selectedTargetId && targets.length > 0) {
      this.selectedTargetId = targets[0].id;
    }
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
      // Find selected target
      const target = this.postTargets().find(t => t.id === this.selectedTargetId);

      // Determine who to post as:
      // - If target is org-wall or org-pet, use the target's orgId
      // - Otherwise, use the "posting as" selector value
      let actingAsOrgId: string | undefined;
      if (target?.type === 'org-wall' || target?.type === 'org-pet') {
        actingAsOrgId = target.orgId;
      } else if (this.postAsOrgId) {
        actingAsOrgId = this.postAsOrgId;
      }

      const newPost = await this.apiService.createPost({
        content: this.newPostContent,
        petId: target?.petId,
        actingAsOrgId,
        imageUrl: this.newPostImageUrl.trim() || undefined
      });

      // Add the new post to the beginning of the feed without reloading everything
      this.posts.update(posts => [newPost, ...posts]);
      this.newPostContent = '';
      this.newPostImageUrl = '';
      // Reset target to default
      if (this.postTargets().length > 0) {
        this.selectedTargetId = this.postTargets()[0].id;
      }
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
      await this.loadData();
    } catch (err: any) {
      alert(err.error?.error || 'Failed to delete post');
    }
  }

  isMyPost(post: Post): boolean {
    // Check if the post was authored by the current user
    if (post.authorId === this.authService.user()?.id) {
      return true;
    }
    // Check if the post was authored by one of the user's organizations
    if (post.authorType === 'organization') {
      return this.myOrgs().some(o => o.organization.id === post.authorId);
    }
    return false;
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
    const org = this.myOrgs().find(o => o.organization.id === this.postAsOrgId);
    return org?.organization.name || 'Unknown';
  }

  getSelectedPostAsInitial(): string {
    return this.getSelectedPostAsName().charAt(0).toUpperCase();
  }

  // Check if posting as an organization (for styling)
  isPostingAsOrg(): boolean {
    return !!this.postAsOrgId;
  }

  // "Where to post" selector methods
  toggleTargetSelector() {
    this.showTargetSelector.update(v => !v);
  }

  selectTarget(targetId: string) {
    this.selectedTargetId = targetId;
    this.showTargetSelector.set(false);
  }

  getSelectedTarget(): PostTarget | undefined {
    return this.postTargets().find(t => t.id === this.selectedTargetId);
  }

  getSelectedTargetLabel(): string {
    const target = this.getSelectedTarget();
    return target?.label || 'Select where to post';
  }

  getTargetIcon(target: PostTarget): string {
    if (target.type === 'my-wall') {
      return this.authService.user()?.name?.charAt(0)?.toUpperCase() || 'M';
    }
    if (target.type === 'my-pet' || target.type === 'org-pet') {
      return '🐾';  // Will be replaced by actual pet emoji in the template
    }
    if (target.type === 'org-wall') {
      const org = this.myOrgs().find(o => o.organization.id === target.orgId);
      return org?.organization.name.charAt(0).toUpperCase() || 'O';
    }
    return '?';
  }

  isTargetOrg(target: PostTarget): boolean {
    return target.type === 'org-wall';
  }

  isTargetPet(target: PostTarget): boolean {
    return target.type === 'my-pet' || target.type === 'org-pet';
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

  getOrgTypeLabel(orgType: string): string {
    const labels: Record<string, string> = {
      'shelter': 'Shelter',
      'rescue': 'Rescue',
      'breeder': 'Breeder',
      'vet_clinic': 'Vet Clinic'
    };
    return labels[orgType] || orgType;
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
