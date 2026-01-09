import { Component, Input } from '@angular/core';
import { CommonModule, TitleCasePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { getSpeciesEmoji, getSpeciesLabel, getSexEmoji, calculateAge } from '../../utils/pet-utils';

export interface PetCardData {
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
  status?: string; // alias for petStatus (used in org pets)
}

@Component({
  selector: 'app-pet-card',
  standalone: true,
  imports: [CommonModule, RouterModule, TitleCasePipe],
  templateUrl: './pet-card.html',
  styleUrls: ['./pet-card.scss']
})
export class PetCardComponent {
  @Input() pet!: PetCardData;
  @Input() showStatus = true; // Show status badge (for adoption, for sale, etc.)

  // Use shared utility functions
  getSpeciesEmoji = getSpeciesEmoji;
  getSpeciesLabel = getSpeciesLabel;
  getSexEmoji = getSexEmoji;
  calculateAge = calculateAge;

  get petStatus(): string | undefined {
    return this.pet.petStatus || this.pet.status;
  }

  get showStatusBadge(): boolean {
    const status = this.petStatus;
    return this.showStatus && !!status && status !== 'owned' && status !== 'current';
  }
}
