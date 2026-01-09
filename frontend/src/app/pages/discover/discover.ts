import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TitleCasePipe } from '@angular/common';
import { ApiService, PetWithOwner, UserSearchResult, Organization } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

export interface PetFilters {
  species?: string;
  ownerType?: 'individual' | 'organization' | '';
  orgType?: string;
}

export interface OrgFilters {
  orgType?: string;
}

@Component({
  selector: 'app-discover',
  imports: [RouterLink, FormsModule, TitleCasePipe],
  templateUrl: './discover.html',
  styleUrl: './discover.scss'
})
export class DiscoverPage implements OnInit {
  private apiService = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  authService = inject(AuthService);

  searchQuery = signal('');
  activeTab = signal<'pets' | 'users' | 'organizations'>('pets');

  // Filter states
  showFilters = signal(false);
  petFilters = signal<PetFilters>({});
  orgFilters = signal<OrgFilters>({});

  // Sort states (field only, direction is separate)
  petSortField = signal<string>('name');
  userSortField = signal<string>('name');
  orgSortField = signal<string>('name');

  // Sort direction states
  petSortDesc = signal(false);
  userSortDesc = signal(false);
  orgSortDesc = signal(false);

  // Filter options
  speciesOptions = ['dog', 'cat', 'bird', 'reptile', 'small_animal', 'other'];
  orgTypeOptions = ['shelter', 'rescue', 'breeder', 'vet_clinic'];

  // Data
  pets = signal<PetWithOwner[]>([]);
  users = signal<UserSearchResult[]>([]);
  organizations = signal<Organization[]>([]);

  // Pagination state for each tab
  petsPage = signal(1);
  petsTotal = signal(0);
  petsTotalPages = signal(1);

  usersPage = signal(1);
  usersTotal = signal(0);
  usersTotalPages = signal(1);

  orgsPage = signal(1);
  orgsTotal = signal(0);
  orgsTotalPages = signal(1);

  // Shared page size (user can change this)
  pageSize = signal(25);
  pageSizeOptions = [10, 25, 50, 100];

  isLoading = signal(false);

  async ngOnInit() {
    // Read filter params from URL
    const params = this.route.snapshot.queryParams;

    // Set tab if specified
    if (params['tab'] === 'users' || params['tab'] === 'organizations') {
      this.activeTab.set(params['tab']);
    }

    // Set pet filters from URL
    if (params['species']) {
      this.petFilters.update(f => ({ ...f, species: params['species'] }));
      this.showFilters.set(true);
    }
    if (params['ownerType']) {
      this.petFilters.update(f => ({ ...f, ownerType: params['ownerType'] }));
      this.showFilters.set(true);
    }

    // Set org filters from URL
    if (params['orgType']) {
      this.orgFilters.update(f => ({ ...f, orgType: params['orgType'] }));
      this.showFilters.set(true);
    }

    await this.loadAll();
  }

  async loadAll() {
    this.isLoading.set(true);
    try {
      const query = this.searchQuery().trim() || undefined;
      const petFilters = this.petFilters();
      const orgFilters = this.orgFilters();

      const [petsResponse, usersResponse, orgsResponse] = await Promise.all([
        this.apiService.getAllPets(
          this.petsPage(),
          this.pageSize(),
          query,
          petFilters.species,
          petFilters.ownerType || undefined,
          petFilters.orgType,
          this.getPetSort()
        ),
        this.apiService.getAllUsers(
          this.usersPage(),
          this.pageSize(),
          query,
          this.getUserSort()
        ),
        this.apiService.getOrganizationsList(
          this.orgsPage(),
          this.pageSize(),
          query,
          orgFilters.orgType,
          this.getOrgSort()
        )
      ]);

      this.pets.set(petsResponse.pets);
      this.petsTotal.set(petsResponse.total);
      this.petsTotalPages.set(petsResponse.totalPages);

      this.users.set(usersResponse.users);
      this.usersTotal.set(usersResponse.total);
      this.usersTotalPages.set(usersResponse.totalPages);

      this.organizations.set(orgsResponse.organizations);
      this.orgsTotal.set(orgsResponse.total);
      this.orgsTotalPages.set(orgsResponse.totalPages);
    } catch (err) {
      console.error('Failed to load data:', err);
    } finally {
      this.isLoading.set(false);
    }
  }

  async search() {
    // Reset to page 1 when searching
    this.petsPage.set(1);
    this.usersPage.set(1);
    this.orgsPage.set(1);
    await this.loadAll();
  }

  async clearSearch() {
    this.searchQuery.set('');
    this.petsPage.set(1);
    this.usersPage.set(1);
    this.orgsPage.set(1);
    await this.loadAll();
  }

  setTab(tab: 'pets' | 'users' | 'organizations') {
    this.activeTab.set(tab);
  }

  toggleFilters() {
    this.showFilters.update(v => !v);
  }

  // Pet filters
  setPetSpeciesFilter(species: string) {
    this.petFilters.update(f => ({ ...f, species: species || undefined }));
    this.petsPage.set(1);
    this.loadPetsWithFilters();
  }

  setPetOwnerTypeFilter(ownerType: string) {
    this.petFilters.update(f => ({
      ...f,
      ownerType: (ownerType as 'individual' | 'organization' | '') || undefined,
      orgType: ownerType !== 'organization' ? undefined : f.orgType
    }));
    this.petsPage.set(1);
    this.loadPetsWithFilters();
  }

  setPetOrgTypeFilter(orgType: string) {
    this.petFilters.update(f => ({ ...f, orgType: orgType || undefined }));
    this.petsPage.set(1);
    this.loadPetsWithFilters();
  }

  setPetSortField(field: string) {
    this.petSortField.set(field);
    this.petsPage.set(1);
    this.loadPetsWithFilters();
  }

  togglePetSortDirection() {
    this.petSortDesc.update(v => !v);
    this.petsPage.set(1);
    this.loadPetsWithFilters();
  }

  getPetSort(): string | undefined {
    const field = this.petSortField();
    if (!field) return undefined;
    // Apply direction to all sortable fields
    return this.petSortDesc() ? `${field}_desc` : `${field}_asc`;
  }

  // Org filters
  setOrgTypeFilter(orgType: string) {
    this.orgFilters.update(f => ({ ...f, orgType: orgType || undefined }));
    this.orgsPage.set(1);
    this.loadOrgsWithFilters();
  }

  setOrgSortField(field: string) {
    this.orgSortField.set(field);
    this.orgsPage.set(1);
    this.loadOrgsWithFilters();
  }

  toggleOrgSortDirection() {
    this.orgSortDesc.update(v => !v);
    this.orgsPage.set(1);
    this.loadOrgsWithFilters();
  }

  getOrgSort(): string | undefined {
    const field = this.orgSortField();
    if (!field) return undefined;
    // Apply direction to all sortable fields
    return this.orgSortDesc() ? `${field}_desc` : `${field}_asc`;
  }

  // User sort
  setUserSortField(field: string) {
    this.userSortField.set(field);
    this.usersPage.set(1);
    this.loadUsersWithFilters();
  }

  toggleUserSortDirection() {
    this.userSortDesc.update(v => !v);
    this.usersPage.set(1);
    this.loadUsersWithFilters();
  }

  getUserSort(): string | undefined {
    const field = this.userSortField();
    if (!field) return undefined;
    // Apply direction to all sortable fields
    return this.userSortDesc() ? `${field}_desc` : `${field}_asc`;
  }

  clearFilters() {
    this.petFilters.set({});
    this.orgFilters.set({});
    this.petSortField.set('name');
    this.petSortDesc.set(false);
    this.userSortField.set('name');
    this.userSortDesc.set(false);
    this.orgSortField.set('name');
    this.orgSortDesc.set(false);
    this.petsPage.set(1);
    this.usersPage.set(1);
    this.orgsPage.set(1);
    this.loadAll();
  }

  hasActiveFilters(): boolean {
    const pf = this.petFilters();
    const of = this.orgFilters();
    return !!(pf.species || pf.ownerType || pf.orgType || of.orgType);
  }

  private async loadPetsWithFilters() {
    this.isLoading.set(true);
    try {
      const query = this.searchQuery().trim() || undefined;
      const filters = this.petFilters();
      const response = await this.apiService.getAllPets(
        this.petsPage(),
        this.pageSize(),
        query,
        filters.species,
        filters.ownerType || undefined,
        filters.orgType,
        this.getPetSort()
      );
      this.pets.set(response.pets);
      this.petsTotal.set(response.total);
      this.petsTotalPages.set(response.totalPages);
    } catch (err) {
      console.error('Failed to load pets:', err);
    } finally {
      this.isLoading.set(false);
    }
  }

  private async loadUsersWithFilters() {
    this.isLoading.set(true);
    try {
      const query = this.searchQuery().trim() || undefined;
      const response = await this.apiService.getAllUsers(
        this.usersPage(),
        this.pageSize(),
        query,
        this.getUserSort()
      );
      this.users.set(response.users);
      this.usersTotal.set(response.total);
      this.usersTotalPages.set(response.totalPages);
    } catch (err) {
      console.error('Failed to load users:', err);
    } finally {
      this.isLoading.set(false);
    }
  }

  private async loadOrgsWithFilters() {
    this.isLoading.set(true);
    try {
      const query = this.searchQuery().trim() || undefined;
      const filters = this.orgFilters();
      const response = await this.apiService.getOrganizationsList(
        this.orgsPage(),
        this.pageSize(),
        query,
        filters.orgType,
        this.getOrgSort()
      );
      this.organizations.set(response.organizations);
      this.orgsTotal.set(response.total);
      this.orgsTotalPages.set(response.totalPages);
    } catch (err) {
      console.error('Failed to load organizations:', err);
    } finally {
      this.isLoading.set(false);
    }
  }

  async changePageSize(newSize: number) {
    this.pageSize.set(newSize);
    // Reset to page 1 when changing page size
    this.petsPage.set(1);
    this.usersPage.set(1);
    this.orgsPage.set(1);
    await this.loadAll();
  }

  // Pets pagination
  async goToPetsPage(page: number) {
    if (page < 1 || page > this.petsTotalPages()) return;
    this.petsPage.set(page);
    await this.loadPets();
  }

  private async loadPets() {
    this.isLoading.set(true);
    try {
      const query = this.searchQuery().trim() || undefined;
      const response = await this.apiService.getAllPets(this.petsPage(), this.pageSize(), query);
      this.pets.set(response.pets);
      this.petsTotal.set(response.total);
      this.petsTotalPages.set(response.totalPages);
    } catch (err) {
      console.error('Failed to load pets:', err);
    } finally {
      this.isLoading.set(false);
    }
  }

  // Users pagination
  async goToUsersPage(page: number) {
    if (page < 1 || page > this.usersTotalPages()) return;
    this.usersPage.set(page);
    await this.loadUsers();
  }

  private async loadUsers() {
    this.isLoading.set(true);
    try {
      const query = this.searchQuery().trim() || undefined;
      const response = await this.apiService.getAllUsers(this.usersPage(), this.pageSize(), query);
      this.users.set(response.users);
      this.usersTotal.set(response.total);
      this.usersTotalPages.set(response.totalPages);
    } catch (err) {
      console.error('Failed to load users:', err);
    } finally {
      this.isLoading.set(false);
    }
  }

  // Organizations pagination
  async goToOrgsPage(page: number) {
    if (page < 1 || page > this.orgsTotalPages()) return;
    this.orgsPage.set(page);
    await this.loadOrgs();
  }

  private async loadOrgs() {
    this.isLoading.set(true);
    try {
      const query = this.searchQuery().trim() || undefined;
      const response = await this.apiService.getOrganizationsList(this.orgsPage(), this.pageSize(), query);
      this.organizations.set(response.organizations);
      this.orgsTotal.set(response.total);
      this.orgsTotalPages.set(response.totalPages);
    } catch (err) {
      console.error('Failed to load organizations:', err);
    } finally {
      this.isLoading.set(false);
    }
  }

  // Helper to get visible page numbers with ellipsis support
  // Returns an array of page numbers and -1 for ellipsis positions
  getVisiblePages(currentPage: number, totalPages: number): number[] {
    if (totalPages <= 7) {
      // Show all pages if total is small
      return Array.from({ length: totalPages }, (_, i) => i + 1);
    }

    const pages: number[] = [];

    // Always show first page
    pages.push(1);

    if (currentPage > 4) {
      // Add ellipsis after first page
      pages.push(-1);
    }

    // Calculate range around current page
    const start = Math.max(2, currentPage - 1);
    const end = Math.min(totalPages - 1, currentPage + 1);

    // Add pages around current
    for (let i = start; i <= end; i++) {
      if (!pages.includes(i)) {
        pages.push(i);
      }
    }

    if (currentPage < totalPages - 3) {
      // Add ellipsis before last page
      pages.push(-1);
    }

    // Always show last page
    if (!pages.includes(totalPages)) {
      pages.push(totalPages);
    }

    return pages;
  }

  // Check if a page number represents an ellipsis
  isEllipsis(page: number): boolean {
    return page === -1;
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

  getUserInitial(name: string): string {
    return name.charAt(0).toUpperCase();
  }

  getOrgTypeLabel(orgType: string): string {
    const labels: Record<string, string> = {
      'shelter': 'Shelter',
      'breeder': 'Breeder',
      'vet_clinic': 'Vet Clinic',
      'rescue': 'Rescue'
    };
    return labels[orgType] || orgType;
  }

  // All sort fields support direction toggle
  sortFieldSupportsDirection(): boolean {
    return true;
  }

  // Navigate to filtered views
  filterBySpecies(species: string, event: Event) {
    event.preventDefault();
    event.stopPropagation();
    this.activeTab.set('pets');
    this.petFilters.set({ species });
    this.petsPage.set(1);
    this.showFilters.set(true);
    this.loadPetsWithFilters();
    // Update URL without navigation
    this.router.navigate([], {
      queryParams: { species, tab: 'pets' },
      queryParamsHandling: 'merge'
    });
  }

  filterByOrgType(orgType: string, event: Event) {
    event.preventDefault();
    event.stopPropagation();
    this.activeTab.set('organizations');
    this.orgFilters.set({ orgType });
    this.orgsPage.set(1);
    this.showFilters.set(true);
    this.loadOrgsWithFilters();
    // Update URL without navigation
    this.router.navigate([], {
      queryParams: { orgType, tab: 'organizations' },
      queryParamsHandling: 'merge'
    });
  }
}
