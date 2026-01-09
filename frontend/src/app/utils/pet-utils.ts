// Shared utility functions for pet-related display

export function getSpeciesEmoji(species: string): string {
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

export function getSpeciesLabel(species: string): string {
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

export function getSexEmoji(sex?: string): string {
  if (sex === 'male') return '♂️';
  if (sex === 'female') return '♀️';
  return '';
}

export function getSexLabel(sex: string): string {
  const labels: Record<string, string> = {
    '': 'Not specified',
    'male': 'Male',
    'female': 'Female',
    'unknown': 'Unknown'
  };
  return labels[sex] || sex;
}

export function getStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    'owned': 'Owned (regular pet)',
    'for_adoption': 'For Adoption',
    'for_sale': 'For Sale',
    'needs_help': 'Needs Help'
  };
  return labels[status] || status;
}

export function getOrgTypeLabel(orgType: string): string {
  const labels: Record<string, string> = {
    'shelter': 'Shelter',
    'rescue': 'Rescue',
    'breeder': 'Breeder',
    'vet_clinic': 'Vet Clinic'
  };
  return labels[orgType] || orgType;
}

export function calculateAge(birthYear?: number, birthMonth?: number, birthDay?: number): string | null {
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

export function formatTime(isoString: string): string {
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
