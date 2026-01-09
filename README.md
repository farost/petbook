# PetBook

A social networking platform for pet lovers built with TypeDB, Kotlin/Ktor, and Angular.

Built for pure technology testing.

## Overview

PetBook is a full-featured social platform where pet owners can connect, share updates about their pets, and discover other pet lovers. Organizations like shelters, rescues, breeders, and veterinary clinics can also create profiles and manage their animals.

## Features

### User Features
- User registration and authentication with JWT
- User profiles with bio, location, and birthday
- Multiple pet ownership
- Follow other users, pets, and organizations
- Personalized social feed
- Post on your own wall, pet walls, or other users' walls

### Pet Features
- Create and manage pets (dogs, cats, birds, reptiles, small animals, other)
- Track pet status: owned, for_adoption, for_sale, needs_help
- Pet profiles with bio, species, breed, sex, and birthday
- Complete ownership history tracking (adoption, surrender, rescue, sale, gift)
- Transfer pets between users and organizations
- Wall posts on pet profiles

### Organization Features
- Create organizations: shelters, rescues, breeders, veterinary clinics
- Role-based management (owner, admin, member)
- Manage organization's pets
- Organization profiles with establishment dates
- Post as organization on pet walls and user walls
- Transfer organization ownership
- Follow organizations

### Social Features
- Create posts with optional images (via URL)
- Feed shows posts from followed users, pets, and organizations
- Click-to-expand fullscreen image viewing
- Discover page with advanced search and filtering
- Pagination support for all lists

## Technology Stack

### Backend
- **Language:** Kotlin 2.1.0
- **Framework:** Ktor 3.0.3
- **Server:** Netty
- **Database:** TypeDB 3.7.0-alpha-3
- **Authentication:** JWT (Auth0)
- **Password Hashing:** BCrypt
- **Build Tool:** Gradle
- **Java:** 21

### Frontend
- **Framework:** Angular 21
- **Language:** TypeScript 5.9
- **Styling:** SCSS
- **Testing:** Vitest

## Project Structure

```
├── backend/
│   ├── src/main/kotlin/com/petbook/
│   │   ├── Application.kt          # Server entry point
│   │   ├── auth/                   # Authentication
│   │   ├── user/                   # User management
│   │   ├── pet/                    # Pet management
│   │   ├── post/                   # Social posts
│   │   ├── follow/                 # Following system
│   │   ├── organization/           # Organizations
│   │   └── db/                     # TypeDB connection
│   ├── build.gradle.kts
│   ├── build.sh
│   ├── run.sh
│   └── seed.sh                     # Test data seeding
│
├── frontend/
│   ├── src/app/
│   │   ├── pages/                  # Route components
│   │   ├── components/             # Reusable UI components
│   │   ├── services/               # API & Auth services
│   │   └── utils/                  # Utility functions
│   ├── angular.json
│   └── package.json
│
├── schema/
│   └── petbook.tql                 # TypeDB schema
│
├── dev.sh                          # Start both frontend & backend
└── README.md
```

## Getting Started

### Prerequisites

- Java 21
- Node.js 18+
- TypeDB 3.x

### Backend Setup

1. **Configure environment:**
   ```bash
   cd backend
   cp .env.example .env
   # Edit .env with your TypeDB credentials
   ```

2. **Environment variables:**
   ```
   TYPEDB_ADDRESSES=localhost:1729
   TYPEDB_DATABASE=petbook
   TYPEDB_USERNAME=admin
   TYPEDB_PASSWORD=your-password
   TYPEDB_USE_TLS=false
   ```

3. **Load schema and build:**
   ```bash
   ./gradlew loadSchema
   ./gradlew build
   ```

4. **Run backend:**
   ```bash
   ./run.sh
   # Or: ./gradlew run
   ```
   Backend runs on `http://localhost:8080`

### Frontend Setup

1. **Install dependencies:**
   ```bash
   cd frontend
   npm install
   ```

2. **Start development server:**
   ```bash
   npm start
   # Or: ng serve
   ```
   Frontend runs on `http://localhost:4200`

### Quick Start (Both)

From the project root:
```bash
./dev.sh
```
This starts both the backend (port 8080) and frontend (port 4200).

### Seed Test Data

To populate the database with sample users, pets, and organizations:
```bash
cd backend
./seed.sh
```

## API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login

### Users
- `GET /api/users` - List users (paginated)
- `GET /api/users/me` - Current user profile
- `GET /api/users/{id}` - User profile
- `PUT /api/users/me` - Update profile

### Pets
- `GET /api/pets` - List all pets (paginated, filterable)
- `GET /api/pets/my` - Current user's pets
- `GET /api/pets/{id}` - Pet details
- `POST /api/pets` - Create pet
- `PUT /api/pets/{id}` - Update pet
- `DELETE /api/pets/{id}` - Delete pet
- `POST /api/pets/{id}/transfer` - Transfer ownership
- `GET /api/pets/{id}/history` - Ownership history

### Posts
- `GET /api/posts/feed` - User's feed
- `POST /api/posts` - Create post (with optional image URL)
- `GET /api/posts/user/{userId}` - User's posts
- `GET /api/posts/pet/{petId}` - Pet's wall posts
- `DELETE /api/posts/{id}` - Delete post

### Follow
- `POST /api/follow/user/{id}` - Follow user
- `DELETE /api/follow/user/{id}` - Unfollow user
- `POST /api/follow/pet/{id}` - Follow pet
- `DELETE /api/follow/pet/{id}` - Unfollow pet
- `POST /api/follow/organization/{id}` - Follow organization
- `DELETE /api/follow/organization/{id}` - Unfollow organization
- `GET /api/follow/following` - Get following list

### Organizations
- `GET /api/organizations` - List organizations
- `GET /api/organizations/my` - User's organizations
- `GET /api/organizations/{id}` - Organization details
- `POST /api/organizations` - Create organization
- `PUT /api/organizations/{id}` - Update organization
- `POST /api/organizations/{id}/transfer` - Transfer ownership

## Database Schema

PetBook uses TypeDB with a rich type system:

**Entities:**
- `individual` - User accounts
- `organization` - Shelters, rescues, breeders, vet clinics
- `pet` - Animals with ownership tracking
- `post` - Social media posts

**Relations:**
- `ownership` - User/org owns pet (with history)
- `org_management` - Users managing organizations
- `follows` - Following relationships
- `authorship` - Post authors
- `pet_post` / `user_wall_post` / `org_wall_post` - Post locations

## Development Commands

### Backend
```bash
./gradlew build              # Build project
./gradlew run                # Run server
./gradlew test               # Run tests
./gradlew loadSchema         # Load TypeDB schema
./gradlew resetDatabase      # Reset database (deletes data!)
./gradlew diagnostic         # Run diagnostics
```

### Frontend
```bash
npm start                    # Dev server
npm run build                # Production build
npm test                     # Run tests
npm run watch                # Watch mode
```

## Contributing

This project was created for the TypeDB Hackathon (January 2026).

## License

MIT
