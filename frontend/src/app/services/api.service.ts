import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';

export interface Pet {
  id: string;
  name: string;
  species: string;
  breed?: string;
  bio?: string;
  petStatus: string;
  sex?: string;           // male, female, unknown
  birthYear?: number;     // For approximate birthdays
  birthMonth?: number;    // 1-12, optional
  birthDay?: number;      // 1-31, optional
  profileImageUrl?: string;
}

export interface PetWithOwner {
  pet: Pet;
  ownerId: string;
  ownerName: string;
  ownerType: string; // "individual" or "organization"
  ownershipStatus: string;
}

export interface UserProfile {
  id: string;
  name: string;
  email: string;
  bio?: string;
  location?: string;
  birthYear?: number;
  birthMonth?: number;
  birthDay?: number;
}

export interface UserProfileWithPets {
  user: UserProfile;
  pets: PetSummary[];
}

export interface PetSummary {
  id: string;
  name: string;
  species: string;
  breed?: string;
  bio?: string;
  sex?: string;
  birthYear?: number;
  birthMonth?: number;
  birthDay?: number;
  petStatus?: string;
}

export interface CreatePetRequest {
  name: string;
  species: string;
  breed?: string;
  bio?: string;
  sex?: string;
  birthYear?: number;
  birthMonth?: number;
  birthDay?: number;
  petStatus?: string;     // owned, for_adoption, for_sale, needs_help
  actingAsOrgId?: string; // If provided, pet is owned by the organization
}

// Post types
export interface Post {
  id: string;
  content: string;
  authorId: string;
  authorName: string;
  authorType: string; // "individual" or "organization"
  petId?: string;
  petName?: string;
  petSpecies?: string;
  createdAt?: string;
  imageUrl?: string;  // Optional image URL attached to the post
}

export interface PostFeed {
  posts: Post[];
}

export interface CreatePostRequest {
  content: string;
  petId?: string;
  actingAsOrgId?: string;   // If provided, post is authored by the organization (from)
  targetUserId?: string;    // If provided, post goes on this user's wall (to)
  targetOrgId?: string;     // If provided, post goes on this org's wall (to)
  imageUrl?: string;        // Optional: URL of image to attach to the post
}

// Follow types
export interface FollowStatus {
  isFollowing: boolean;
  followerCount: number;
}

export interface FollowingList {
  users: { id: string; name: string; type: string }[];
  pets: { id: string; name: string; species: string; type: string }[];
}

export interface UserSearchResult {
  id: string;
  name: string;
  email: string;
  petCount?: number;
}

// Organization types
export interface Organization {
  id: string;
  name: string;
  orgType: string; // shelter, rescue, breeder, vet_clinic
  bio?: string;
  location?: string;
  petCount?: number;
  establishedYear?: number;
  establishedMonth?: number;
  establishedDay?: number;
}

export interface OrganizationWithRole {
  organization: Organization;
  role: string; // owner, admin, member
}

export interface OrgPetSummary {
  id: string;
  name: string;
  species: string;
  status: string;
  breed?: string;
  bio?: string;
  sex?: string;
  birthYear?: number;
  birthMonth?: number;
  birthDay?: number;
}

export interface OrganizationWithPets {
  organization: Organization;
  pets: OrgPetSummary[];
  followerCount: number;
}

export interface CreateOrganizationRequest {
  name: string;
  orgType: string;
  bio?: string;
  location?: string;
}

// Pet transfer types
export interface TransferPetRequest {
  toUserId?: string;
  toOrgId?: string;
  reason: string; // adoption, surrender, rescue, sale, gift
}

// Paginated response types
export interface PaginatedPetsResponse {
  pets: PetWithOwner[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface PaginatedUsersResponse {
  users: UserSearchResult[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface PaginatedOrganizationsResponse {
  organizations: Organization[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface OwnershipRecord {
  ownerId: string;
  ownerName: string;
  ownerType: string;
  status: string;
  startDate?: string;
  endDate?: string;
  transferReason?: string;
}

export interface OwnershipHistoryResponse {
  petId: string;
  petName: string;
  history: OwnershipRecord[];
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly API_URL = 'http://localhost:8080/api';
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  private getAuthHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : ''
    });
  }

  // User endpoints
  async getMyProfile(): Promise<UserProfileWithPets> {
    return firstValueFrom(
      this.http.get<UserProfileWithPets>(`${this.API_URL}/users/me`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getUserProfile(userId: string): Promise<UserProfileWithPets> {
    return firstValueFrom(
      this.http.get<UserProfileWithPets>(`${this.API_URL}/users/${userId}`)
    );
  }

  async updateProfile(data: {
    name?: string;
    bio?: string;
    location?: string;
    birthYear?: number;
    birthMonth?: number;
    birthDay?: number;
    clearBirthday?: boolean;
    clearBirthMonth?: boolean;
    clearBirthDay?: boolean;
  }): Promise<UserProfile> {
    return firstValueFrom(
      this.http.put<UserProfile>(`${this.API_URL}/users/me`, data, {
        headers: this.getAuthHeaders()
      })
    );
  }

  // Pet endpoints
  async getMyPets(): Promise<Pet[]> {
    return firstValueFrom(
      this.http.get<Pet[]>(`${this.API_URL}/pets/my`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getPet(petId: string): Promise<PetWithOwner> {
    return firstValueFrom(
      this.http.get<PetWithOwner>(`${this.API_URL}/pets/${petId}`)
    );
  }

  async createPet(data: CreatePetRequest): Promise<Pet> {
    return firstValueFrom(
      this.http.post<Pet>(`${this.API_URL}/pets`, data, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async updatePet(petId: string, data: Partial<CreatePetRequest>): Promise<void> {
    return firstValueFrom(
      this.http.put<void>(`${this.API_URL}/pets/${petId}`, data, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async deletePet(petId: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`${this.API_URL}/pets/${petId}`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  // Post endpoints
  async getFeed(): Promise<PostFeed> {
    return firstValueFrom(
      this.http.get<PostFeed>(`${this.API_URL}/posts/feed`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async createPost(data: CreatePostRequest): Promise<Post> {
    return firstValueFrom(
      this.http.post<Post>(`${this.API_URL}/posts`, data, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getPostsByUser(userId: string): Promise<PostFeed> {
    return firstValueFrom(
      this.http.get<PostFeed>(`${this.API_URL}/posts/user/${userId}`)
    );
  }

  async getPostsForPet(petId: string): Promise<PostFeed> {
    return firstValueFrom(
      this.http.get<PostFeed>(`${this.API_URL}/posts/pet/${petId}`)
    );
  }

  async deletePost(postId: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`${this.API_URL}/posts/${postId}`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  // Follow endpoints
  async followUser(userId: string): Promise<FollowStatus> {
    return firstValueFrom(
      this.http.post<FollowStatus>(`${this.API_URL}/follow/user/${userId}`, {}, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async unfollowUser(userId: string): Promise<FollowStatus> {
    return firstValueFrom(
      this.http.delete<FollowStatus>(`${this.API_URL}/follow/user/${userId}`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getUserFollowStatus(userId: string): Promise<FollowStatus> {
    return firstValueFrom(
      this.http.get<FollowStatus>(`${this.API_URL}/follow/user/${userId}/status`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async followPet(petId: string): Promise<FollowStatus> {
    return firstValueFrom(
      this.http.post<FollowStatus>(`${this.API_URL}/follow/pet/${petId}`, {}, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async unfollowPet(petId: string): Promise<FollowStatus> {
    return firstValueFrom(
      this.http.delete<FollowStatus>(`${this.API_URL}/follow/pet/${petId}`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getPetFollowStatus(petId: string): Promise<FollowStatus> {
    return firstValueFrom(
      this.http.get<FollowStatus>(`${this.API_URL}/follow/pet/${petId}/status`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getFollowing(): Promise<FollowingList> {
    return firstValueFrom(
      this.http.get<FollowingList>(`${this.API_URL}/follow/following`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  // Discover endpoints with pagination, filtering, and sorting
  async getAllPets(
    page: number = 1,
    pageSize: number = 25,
    query?: string,
    species?: string,
    ownerType?: string,
    orgType?: string,
    sort?: string
  ): Promise<PaginatedPetsResponse> {
    let url = `${this.API_URL}/pets?page=${page}&pageSize=${pageSize}`;
    if (query) url += `&q=${encodeURIComponent(query)}`;
    if (species) url += `&species=${encodeURIComponent(species)}`;
    if (ownerType) url += `&ownerType=${encodeURIComponent(ownerType)}`;
    if (orgType) url += `&orgType=${encodeURIComponent(orgType)}`;
    if (sort) url += `&sort=${encodeURIComponent(sort)}`;
    return firstValueFrom(
      this.http.get<PaginatedPetsResponse>(url)
    );
  }

  async getAllUsers(
    page: number = 1,
    pageSize: number = 25,
    query?: string,
    sort?: string
  ): Promise<PaginatedUsersResponse> {
    let url = `${this.API_URL}/users?page=${page}&pageSize=${pageSize}`;
    if (query) url += `&q=${encodeURIComponent(query)}`;
    if (sort) url += `&sort=${encodeURIComponent(sort)}`;
    return firstValueFrom(
      this.http.get<PaginatedUsersResponse>(url)
    );
  }

  // Organization endpoints
  async getMyOrganizations(): Promise<OrganizationWithRole[]> {
    return firstValueFrom(
      this.http.get<OrganizationWithRole[]>(`${this.API_URL}/organizations/my`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getOrganization(orgId: string): Promise<OrganizationWithPets> {
    return firstValueFrom(
      this.http.get<OrganizationWithPets>(`${this.API_URL}/organizations/${orgId}`)
    );
  }

  async createOrganization(data: CreateOrganizationRequest): Promise<Organization> {
    return firstValueFrom(
      this.http.post<Organization>(`${this.API_URL}/organizations`, data, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async updateOrganization(orgId: string, data: Partial<CreateOrganizationRequest> & {
    establishedYear?: number;
    establishedMonth?: number;
    establishedDay?: number;
    clearEstablishedDate?: boolean;
  }): Promise<void> {
    return firstValueFrom(
      this.http.put<void>(`${this.API_URL}/organizations/${orgId}`, data, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async transferOrganization(orgId: string, toUserId: string): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${this.API_URL}/organizations/${orgId}/transfer`, { toUserId }, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getOrganizationFeed(orgId: string): Promise<PostFeed> {
    return firstValueFrom(
      this.http.get<PostFeed>(`${this.API_URL}/organizations/${orgId}/feed`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getAllOrganizations(query?: string): Promise<OrganizationWithPets[]> {
    const url = query ? `${this.API_URL}/organizations?q=${encodeURIComponent(query)}` : `${this.API_URL}/organizations`;
    return firstValueFrom(
      this.http.get<OrganizationWithPets[]>(url)
    );
  }

  async getOrganizationsList(
    page: number = 1,
    pageSize: number = 25,
    query?: string,
    orgType?: string,
    sort?: string
  ): Promise<PaginatedOrganizationsResponse> {
    let url = `${this.API_URL}/organizations?page=${page}&pageSize=${pageSize}`;
    if (query) url += `&q=${encodeURIComponent(query)}`;
    if (orgType) url += `&orgType=${encodeURIComponent(orgType)}`;
    if (sort) url += `&sort=${encodeURIComponent(sort)}`;
    return firstValueFrom(
      this.http.get<PaginatedOrganizationsResponse>(url)
    );
  }

  async followOrganization(orgId: string): Promise<FollowStatus> {
    return firstValueFrom(
      this.http.post<FollowStatus>(`${this.API_URL}/follow/organization/${orgId}`, {}, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async unfollowOrganization(orgId: string): Promise<FollowStatus> {
    return firstValueFrom(
      this.http.delete<FollowStatus>(`${this.API_URL}/follow/organization/${orgId}`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getOrganizationFollowStatus(orgId: string): Promise<FollowStatus> {
    return firstValueFrom(
      this.http.get<FollowStatus>(`${this.API_URL}/follow/organization/${orgId}/status`, {
        headers: this.getAuthHeaders()
      })
    );
  }

  // Pet transfer endpoints
  async transferPet(petId: string, data: TransferPetRequest): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${this.API_URL}/pets/${petId}/transfer`, data, {
        headers: this.getAuthHeaders()
      })
    );
  }

  async getPetOwnershipHistory(petId: string): Promise<OwnershipHistoryResponse> {
    return firstValueFrom(
      this.http.get<OwnershipHistoryResponse>(`${this.API_URL}/pets/${petId}/history`)
    );
  }
}
