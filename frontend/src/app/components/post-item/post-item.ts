import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Post } from '../../services/api.service';
import { getSpeciesEmoji, formatTime } from '../../utils/pet-utils';

@Component({
  selector: 'app-post-item',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './post-item.html',
  styleUrls: ['./post-item.scss']
})
export class PostItemComponent {
  @Input() post!: Post;
  @Input() showDeleteButton = false;
  @Input() showTarget = true; // Show where the post was posted (pet wall, user wall, etc.)
  @Output() delete = new EventEmitter<string>();

  // Use shared utility functions
  getSpeciesEmoji = getSpeciesEmoji;
  formatTime = formatTime;

  // Image fullscreen state
  isImageFullscreen = false;

  onDelete(): void {
    this.delete.emit(this.post.id);
  }

  toggleImageFullscreen(): void {
    this.isImageFullscreen = !this.isImageFullscreen;
    // Prevent body scroll when fullscreen is open
    if (this.isImageFullscreen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
  }

  handleImageError(event: Event): void {
    // Hide the image container if image fails to load
    const target = event.target as HTMLImageElement;
    if (target && target.parentElement) {
      target.parentElement.style.display = 'none';
    }
  }
}
