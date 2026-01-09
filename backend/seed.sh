#!/bin/bash

# Comprehensive seed script for Petbook test data
# Creates users, pets, organizations, posts, follows with rich stories

API_URL="http://localhost:8080/api"

echo "=== Seeding Petbook Database with Rich Test Data ==="

# Helper function to extract ID from JSON response (handles pretty-printed JSON)
get_id() {
  echo "$1" | tr -d '\n' | tr -d ' ' | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4
}

get_token() {
  echo "$1" | tr -d '\n' | tr -d ' ' | grep -o '"token":"[^"]*"' | cut -d'"' -f4
}

# ==================== USERS ====================
echo ""
echo "=== Creating Users ==="

# User 1: Alice - Dog lover, very active poster
ALICE_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@test.com", "password": "password123", "name": "Alice Johnson"}')
ALICE_TOKEN=$(get_token "$ALICE_RESPONSE")
ALICE_ID=$(get_id "$ALICE_RESPONSE")
curl -s -X PUT "$API_URL/users/me" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d '{"name": "Alice Johnson", "bio": "Dog trainer and animal lover. Rescued my first dog 10 years ago and never looked back!", "location": "San Francisco, CA"}' > /dev/null
echo "Created Alice Johnson - Dog trainer in San Francisco"

# User 2: Bob - Cat person, shelter volunteer
BOB_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email": "bob@test.com", "password": "password123", "name": "Bob Smith"}')
BOB_TOKEN=$(get_token "$BOB_RESPONSE")
BOB_ID=$(get_id "$BOB_RESPONSE")
curl -s -X PUT "$API_URL/users/me" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d '{"name": "Bob Smith", "bio": "Volunteer at Happy Paws Shelter. Fostered over 50 cats in the past 5 years.", "location": "Portland, OR"}' > /dev/null
echo "Created Bob Smith - Shelter volunteer in Portland"

# User 3: Charlie - Bird enthusiast
CHARLIE_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email": "charlie@test.com", "password": "password123", "name": "Charlie Brown"}')
CHARLIE_TOKEN=$(get_token "$CHARLIE_RESPONSE")
CHARLIE_ID=$(get_id "$CHARLIE_RESPONSE")
curl -s -X PUT "$API_URL/users/me" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d '{"name": "Charlie Brown", "bio": "Avian veterinarian specializing in parrots and exotic birds. My cockatoo Coco runs the house!", "location": "Austin, TX"}' > /dev/null
echo "Created Charlie Brown - Avian vet in Austin"

# User 4: Diana - Reptile expert
DIANA_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email": "diana@test.com", "password": "password123", "name": "Diana Prince"}')
DIANA_TOKEN=$(get_token "$DIANA_RESPONSE")
DIANA_ID=$(get_id "$DIANA_RESPONSE")
curl -s -X PUT "$API_URL/users/me" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d '{"name": "Diana Prince", "bio": "Herpetologist and reptile rescue coordinator. Education is key to conservation!", "location": "Miami, FL"}' > /dev/null
echo "Created Diana Prince - Herpetologist in Miami"

# User 5: Emma - Multi-pet household
EMMA_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email": "emma@test.com", "password": "password123", "name": "Emma Wilson"}')
EMMA_TOKEN=$(get_token "$EMMA_RESPONSE")
EMMA_ID=$(get_id "$EMMA_RESPONSE")
curl -s -X PUT "$API_URL/users/me" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d '{"name": "Emma Wilson", "bio": "Living the zoo life with 3 dogs, 2 cats, a rabbit, and a parrot. Chaos coordinator.", "location": "Denver, CO"}' > /dev/null
echo "Created Emma Wilson - Multi-pet owner in Denver"

# User 6: Frank - Professional breeder
FRANK_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email": "frank@test.com", "password": "password123", "name": "Frank Miller"}')
FRANK_TOKEN=$(get_token "$FRANK_RESPONSE")
FRANK_ID=$(get_id "$FRANK_RESPONSE")
curl -s -X PUT "$API_URL/users/me" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FRANK_TOKEN" \
  -d '{"name": "Frank Miller", "bio": "Third-generation Golden Retriever breeder. AKC certified. Health testing advocate.", "location": "Nashville, TN"}' > /dev/null
echo "Created Frank Miller - Professional breeder in Nashville"

# User 7: Grace - Veterinarian
GRACE_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email": "grace@test.com", "password": "password123", "name": "Dr. Grace Lee"}')
GRACE_TOKEN=$(get_token "$GRACE_RESPONSE")
GRACE_ID=$(get_id "$GRACE_RESPONSE")
curl -s -X PUT "$API_URL/users/me" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRACE_TOKEN" \
  -d '{"name": "Dr. Grace Lee", "bio": "Emergency veterinarian at City Pet Hospital. Passionate about senior pet care.", "location": "Seattle, WA"}' > /dev/null
echo "Created Dr. Grace Lee - Veterinarian in Seattle"

# User 8: Henry - First-time pet owner
HENRY_RESPONSE=$(curl -s -X POST "$API_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email": "henry@test.com", "password": "password123", "name": "Henry Chen"}')
HENRY_TOKEN=$(get_token "$HENRY_RESPONSE")
HENRY_ID=$(get_id "$HENRY_RESPONSE")
curl -s -X PUT "$API_URL/users/me" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $HENRY_TOKEN" \
  -d '{"name": "Henry Chen", "bio": "New puppy dad! Learning everything about dog parenting. Tips welcome!", "location": "Chicago, IL"}' > /dev/null
echo "Created Henry Chen - New pet owner in Chicago"

# ==================== ORGANIZATIONS ====================
echo ""
echo "=== Creating Organizations ==="

# Org 1: Happy Paws Shelter (Bob is owner)
ORG1_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d '{"name": "Happy Paws Shelter", "orgType": "shelter", "bio": "No-kill shelter serving Portland since 1995. We believe every pet deserves a loving home. Over 500 successful adoptions last year!", "location": "Portland, OR"}')
SHELTER_ID=$(get_id "$ORG1_RESPONSE")
echo "Created Happy Paws Shelter (Bob manages)"

# Org 2: Exotic Rescue Foundation (Diana is owner)
ORG2_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d '{"name": "Exotic Rescue Foundation", "orgType": "rescue", "bio": "Specializing in reptile and exotic animal rescue. We rehabilitate and rehome surrendered and confiscated exotic pets.", "location": "Miami, FL"}')
RESCUE_ID=$(get_id "$ORG2_RESPONSE")
echo "Created Exotic Rescue Foundation (Diana manages)"

# Org 3: Golden Dreams Kennel (Frank is owner)
ORG3_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FRANK_TOKEN" \
  -d '{"name": "Golden Dreams Kennel", "orgType": "breeder", "bio": "Ethical Golden Retriever breeding for 30 years. All puppies come with health guarantees and lifetime support.", "location": "Nashville, TN"}')
BREEDER_ID=$(get_id "$ORG3_RESPONSE")
echo "Created Golden Dreams Kennel (Frank manages)"

# Org 4: City Pet Hospital (Grace is owner)
ORG4_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRACE_TOKEN" \
  -d '{"name": "City Pet Hospital", "orgType": "vet_clinic", "bio": "24/7 emergency veterinary care. State-of-the-art facility with board-certified specialists.", "location": "Seattle, WA"}')
VET_ID=$(get_id "$ORG4_RESPONSE")
echo "Created City Pet Hospital (Dr. Grace manages)"

# Org 5: Feathered Friends Sanctuary (Charlie is owner)
ORG5_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d '{"name": "Feathered Friends Sanctuary", "orgType": "rescue", "bio": "Parrot and exotic bird rescue. We provide lifetime sanctuary for birds who cannot be rehomed.", "location": "Austin, TX"}')
BIRD_RESCUE_ID=$(get_id "$ORG5_RESPONSE")
echo "Created Feathered Friends Sanctuary (Charlie manages)"

# Org 6: Paws & Claws Rescue (Alice is owner)
ORG6_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d '{"name": "Paws & Claws Rescue", "orgType": "rescue", "bio": "Dedicated to rescuing and rehoming dogs from high-kill shelters across the country. Foster-based rescue since 2015.", "location": "San Francisco, CA"}')
PAWS_RESCUE_ID=$(get_id "$ORG6_RESPONSE")
echo "Created Paws & Claws Rescue (Alice manages)"

# Org 7: Whisker Haven Cat Cafe (Bob is owner)
ORG7_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d '{"name": "Whisker Haven Cat Cafe", "orgType": "shelter", "bio": "A cat cafe where all cats are adoptable! Come enjoy coffee and find your new feline friend.", "location": "Portland, OR"}')
CAT_CAFE_ID=$(get_id "$ORG7_RESPONSE")
echo "Created Whisker Haven Cat Cafe (Bob manages)"

# Org 8: Scales & Tails Reptile Shop (Diana is owner)
ORG8_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d '{"name": "Scales & Tails Reptile Shop", "orgType": "breeder", "bio": "Ethical reptile breeding focusing on leopard geckos and corn snakes. All animals captive-bred with health guarantees.", "location": "Miami, FL"}')
REPTILE_SHOP_ID=$(get_id "$ORG8_RESPONSE")
echo "Created Scales & Tails Reptile Shop (Diana manages)"

# Org 9: Mountain View Animal Hospital (Grace is owner)
ORG9_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRACE_TOKEN" \
  -d '{"name": "Mountain View Animal Hospital", "orgType": "vet_clinic", "bio": "Full-service veterinary hospital with specialists in surgery, dentistry, and oncology.", "location": "Seattle, WA"}')
MOUNTAIN_VET_ID=$(get_id "$ORG9_RESPONSE")
echo "Created Mountain View Animal Hospital (Grace manages)"

# Org 10: Bunny Bunch Rescue (Emma is owner)
ORG10_RESPONSE=$(curl -s -X POST "$API_URL/organizations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d '{"name": "Bunny Bunch Rescue", "orgType": "rescue", "bio": "Rabbit rescue and sanctuary. We specialize in bonding pairs and educating about rabbit care.", "location": "Denver, CO"}')
BUNNY_RESCUE_ID=$(get_id "$ORG10_RESPONSE")
echo "Created Bunny Bunch Rescue (Emma manages)"

# ==================== PETS - Individual Owners ====================
echo ""
echo "=== Creating Pets for Individual Owners ==="

# Alice's pets (dog trainer)
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d '{"name": "Max", "species": "dog", "breed": "Golden Retriever", "bio": "My first rescue! Max was found abandoned at 6 months old. Now he is a certified therapy dog who visits hospitals.", "sex": "male", "birthYear": 2018, "birthMonth": 3, "petStatus": "owned"}')
MAX_ID=$(get_id "$PET_RESPONSE")
echo "Created Max - Alice's therapy dog"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d '{"name": "Bella", "species": "dog", "breed": "Border Collie", "bio": "Agility champion! Bella has won 3 regional competitions. She is incredibly smart and keeps me on my toes.", "sex": "female", "birthYear": 2020, "birthMonth": 7, "petStatus": "owned"}')
BELLA_ID=$(get_id "$PET_RESPONSE")
echo "Created Bella - Alice's agility champion"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d '{"name": "Rocky", "species": "dog", "breed": "German Shepherd", "bio": "Former police K9 who retired early due to hip dysplasia. Now enjoying his golden years with lots of treats.", "sex": "male", "birthYear": 2016, "birthMonth": 11, "petStatus": "owned"}')
ROCKY_ID=$(get_id "$PET_RESPONSE")
echo "Created Rocky - Alice's retired K9"

# Bob's pets (cat person)
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d '{"name": "Whiskers", "species": "cat", "breed": "Maine Coon", "bio": "The gentle giant of the house. Whiskers weighs 22 lbs and thinks he is a lap cat.", "sex": "male", "birthYear": 2019, "birthMonth": 5, "petStatus": "owned"}')
WHISKERS_ID=$(get_id "$PET_RESPONSE")
echo "Created Whiskers - Bob's gentle giant"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d '{"name": "Shadow", "species": "cat", "breed": "Bombay", "bio": "A sleek black cat who lives up to her name. She appears out of nowhere and demands attention.", "sex": "female", "birthYear": 2021, "birthMonth": 10, "petStatus": "owned"}')
SHADOW_ID=$(get_id "$PET_RESPONSE")
echo "Created Shadow - Bob's mysterious cat"

# Charlie's birds
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d '{"name": "Coco", "species": "bird", "breed": "Umbrella Cockatoo", "bio": "My 25-year-old diva! Coco can say over 100 words and loves to dance. She has been with me since vet school.", "sex": "female", "birthYear": 2001, "birthMonth": 2, "petStatus": "owned"}')
COCO_ID=$(get_id "$PET_RESPONSE")
echo "Created Coco - Charlie's cockatoo companion"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d '{"name": "Mango", "species": "bird", "breed": "Sun Conure", "bio": "The loudest alarm clock ever! Mango greets every sunrise with enthusiasm. Beautiful orange and yellow feathers.", "sex": "male", "birthYear": 2022, "birthMonth": 6, "petStatus": "owned"}')
MANGO_ID=$(get_id "$PET_RESPONSE")
echo "Created Mango - Charlie's sun conure"

# Diana's reptiles
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d '{"name": "Draco", "species": "reptile", "breed": "Bearded Dragon", "bio": "My ambassador animal for educational programs. Draco has met thousands of students and loves showing off.", "sex": "male", "birthYear": 2020, "birthMonth": 4, "petStatus": "owned"}')
DRACO_ID=$(get_id "$PET_RESPONSE")
echo "Created Draco - Diana's education ambassador"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d '{"name": "Medusa", "species": "reptile", "breed": "Ball Python", "bio": "A beautiful morph with stunning patterns. Despite her name, she is incredibly docile and loves chin scratches.", "sex": "female", "birthYear": 2019, "birthMonth": 8, "petStatus": "owned"}')
MEDUSA_ID=$(get_id "$PET_RESPONSE")
echo "Created Medusa - Diana's ball python"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d '{"name": "Sheldon", "species": "reptile", "breed": "Red-Eared Slider", "bio": "Rescued from a carnival when he was the size of a quarter. Now he rules his 200-gallon kingdom.", "sex": "male", "birthYear": 2015, "petStatus": "owned"}')
SHELDON_ID=$(get_id "$PET_RESPONSE")
echo "Created Sheldon - Diana's rescue turtle"

# Emma's multi-pet household
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d '{"name": "Duke", "species": "dog", "breed": "Great Dane", "bio": "The gentle giant who thinks he is a Chihuahua. Loves cuddles on the couch despite being 150 lbs.", "sex": "male", "birthYear": 2021, "birthMonth": 1, "petStatus": "owned"}')
DUKE_ID=$(get_id "$PET_RESPONSE")
echo "Created Duke - Emma's Great Dane"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d '{"name": "Peanut", "species": "dog", "breed": "Chihuahua", "bio": "Bosses everyone around, including Duke. Small but mighty with a huge personality.", "sex": "female", "birthYear": 2020, "birthMonth": 9, "petStatus": "owned"}')
PEANUT_ID=$(get_id "$PET_RESPONSE")
echo "Created Peanut - Emma's Chihuahua"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d '{"name": "Mittens", "species": "cat", "breed": "Ragdoll", "bio": "The peacekeeper of the house. Mittens grooms everyone and has endless patience.", "sex": "female", "birthYear": 2019, "birthMonth": 12, "petStatus": "owned"}')
MITTENS_ID=$(get_id "$PET_RESPONSE")
echo "Created Mittens - Emma's Ragdoll cat"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d '{"name": "Bun Bun", "species": "small_animal", "breed": "Holland Lop", "bio": "Free-roaming house bunny! Litter trained and full of binkies. Best friends with Mittens.", "sex": "female", "birthYear": 2022, "birthMonth": 4, "petStatus": "owned"}')
BUNBUN_ID=$(get_id "$PET_RESPONSE")
echo "Created Bun Bun - Emma's house rabbit"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d '{"name": "Captain", "species": "bird", "breed": "African Grey", "bio": "The smartest member of the family. Captain can mimic all the other pets sounds and loves causing chaos.", "sex": "male", "birthYear": 2010, "petStatus": "owned"}')
CAPTAIN_ID=$(get_id "$PET_RESPONSE")
echo "Created Captain - Emma's African Grey"

# Henry's first pet
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $HENRY_TOKEN" \
  -d '{"name": "Buddy", "species": "dog", "breed": "Labrador Retriever", "bio": "My first ever dog! Got him from Golden Dreams Kennel. We are learning everything together.", "sex": "male", "birthYear": 2024, "birthMonth": 1, "petStatus": "owned"}')
BUDDY_ID=$(get_id "$PET_RESPONSE")
echo "Created Buddy - Henry's first puppy"

# ==================== PETS - Organization Owned ====================
echo ""
echo "=== Creating Pets for Organizations ==="

# Happy Paws Shelter pets (for adoption)
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"name\": \"Luna\", \"species\": \"dog\", \"breed\": \"Husky Mix\", \"bio\": \"Found as a stray, Luna is looking for an active family. She loves to run and needs lots of exercise!\", \"sex\": \"female\", \"birthYear\": 2022, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$SHELTER_ID\"}")
LUNA_ID=$(get_id "$PET_RESPONSE")
echo "Created Luna - Shelter dog for adoption"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"name\": \"Oliver\", \"species\": \"cat\", \"breed\": \"Orange Tabby\", \"bio\": \"Sweet senior boy surrendered when owner moved. Oliver is 12 but has plenty of love left to give.\", \"sex\": \"male\", \"birthYear\": 2013, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$SHELTER_ID\"}")
OLIVER_ID=$(get_id "$PET_RESPONSE")
echo "Created Oliver - Senior shelter cat"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"name\": \"Bonnie\", \"species\": \"dog\", \"breed\": \"Pit Bull Mix\", \"bio\": \"The wiggliest pittie you ever met! Bonnie was returned after her family had a baby but she is great with kids.\", \"sex\": \"female\", \"birthYear\": 2021, \"birthMonth\": 6, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$SHELTER_ID\"}")
BONNIE_ID=$(get_id "$PET_RESPONSE")
echo "Created Bonnie - Shelter pittie"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"name\": \"Clyde\", \"species\": \"dog\", \"breed\": \"Pit Bull Mix\", \"bio\": \"Bonnie's bonded brother! These two must be adopted together. Double the love, double the fun.\", \"sex\": \"male\", \"birthYear\": 2021, \"birthMonth\": 6, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$SHELTER_ID\"}")
CLYDE_ID=$(get_id "$PET_RESPONSE")
echo "Created Clyde - Shelter pittie (bonded with Bonnie)"

# Exotic Rescue Foundation pets
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"name\": \"Spike\", \"species\": \"reptile\", \"breed\": \"Green Iguana\", \"bio\": \"Confiscated from illegal trade. Spike needs an experienced reptile keeper with proper enclosure setup.\", \"sex\": \"male\", \"birthYear\": 2020, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$RESCUE_ID\"}")
SPIKE_ID=$(get_id "$PET_RESPONSE")
echo "Created Spike - Rescue iguana"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"name\": \"Slinky\", \"species\": \"reptile\", \"breed\": \"Corn Snake\", \"bio\": \"Perfect beginner snake! Slinky was surrendered by a college student. Very handleable and eats well.\", \"sex\": \"female\", \"birthYear\": 2023, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$RESCUE_ID\"}")
SLINKY_ID=$(get_id "$PET_RESPONSE")
echo "Created Slinky - Rescue corn snake"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"name\": \"Rex\", \"species\": \"reptile\", \"breed\": \"Tegu\", \"bio\": \"NEEDS HELP: Rex was found severely underweight and with MBD. Currently in rehabilitation. Sponsor his care!\", \"sex\": \"male\", \"birthYear\": 2019, \"petStatus\": \"needs_help\", \"actingAsOrgId\": \"$RESCUE_ID\"}")
REX_ID=$(get_id "$PET_RESPONSE")
echo "Created Rex - Tegu needing medical help"

# Golden Dreams Kennel puppies (for sale)
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FRANK_TOKEN" \
  -d "{\"name\": \"Goldie\", \"species\": \"dog\", \"breed\": \"Golden Retriever\", \"bio\": \"From our champion bloodline! Goldie is ready for her forever home. All health clearances done.\", \"sex\": \"female\", \"birthYear\": 2024, \"birthMonth\": 9, \"petStatus\": \"for_sale\", \"actingAsOrgId\": \"$BREEDER_ID\"}")
GOLDIE_ID=$(get_id "$PET_RESPONSE")
echo "Created Goldie - Breeder puppy for sale"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FRANK_TOKEN" \
  -d "{\"name\": \"Scout\", \"species\": \"dog\", \"breed\": \"Golden Retriever\", \"bio\": \"The adventurer of the litter! Scout shows great potential for agility or hunting. Very smart boy.\", \"sex\": \"male\", \"birthYear\": 2024, \"birthMonth\": 9, \"petStatus\": \"for_sale\", \"actingAsOrgId\": \"$BREEDER_ID\"}")
SCOUT_ID=$(get_id "$PET_RESPONSE")
echo "Created Scout - Breeder puppy for sale"

# Feathered Friends Sanctuary birds
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d "{\"name\": \"Einstein\", \"species\": \"bird\", \"breed\": \"African Grey\", \"bio\": \"Permanent sanctuary resident. Einstein is 40 years old and has outlived 2 owners. He is happy here.\", \"sex\": \"male\", \"birthYear\": 1985, \"petStatus\": \"owned\", \"actingAsOrgId\": \"$BIRD_RESCUE_ID\"}")
EINSTEIN_ID=$(get_id "$PET_RESPONSE")
echo "Created Einstein - Sanctuary's elder African Grey"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d "{\"name\": \"Pepper\", \"species\": \"bird\", \"breed\": \"Macaw\", \"bio\": \"Looking for an experienced parrot person! Pepper needs someone who understands macaw behavior.\", \"sex\": \"female\", \"birthYear\": 2015, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$BIRD_RESCUE_ID\"}")
PEPPER_ID=$(get_id "$PET_RESPONSE")
echo "Created Pepper - Sanctuary macaw for adoption"

# Paws & Claws Rescue dogs (Alice manages)
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"name\": \"Daisy\", \"species\": \"dog\", \"breed\": \"Beagle Mix\", \"bio\": \"Sweet senior girl who loves belly rubs and naps. Perfect for a quiet home.\", \"sex\": \"female\", \"birthYear\": 2015, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$PAWS_RESCUE_ID\"}")
echo "Created Daisy - Paws & Claws senior beagle"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"name\": \"Thor\", \"species\": \"dog\", \"breed\": \"Mastiff Mix\", \"bio\": \"Gentle giant with a heart of gold. Thor needs a home with a big couch!\", \"sex\": \"male\", \"birthYear\": 2021, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$PAWS_RESCUE_ID\"}")
echo "Created Thor - Paws & Claws mastiff mix"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"name\": \"Ziggy\", \"species\": \"dog\", \"breed\": \"Jack Russell Terrier\", \"bio\": \"High energy pup who loves agility and fetch. Needs an active family!\", \"sex\": \"male\", \"birthYear\": 2023, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$PAWS_RESCUE_ID\"}")
echo "Created Ziggy - Paws & Claws terrier"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"name\": \"Rosie\", \"species\": \"dog\", \"breed\": \"Cocker Spaniel\", \"bio\": \"NEEDS HELP: Rosie requires surgery for a torn ACL. Help us give her a pain-free life!\", \"sex\": \"female\", \"birthYear\": 2020, \"petStatus\": \"needs_help\", \"actingAsOrgId\": \"$PAWS_RESCUE_ID\"}")
echo "Created Rosie - Paws & Claws spaniel needing surgery"

# Whisker Haven Cat Cafe cats (Bob manages)
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"name\": \"Mocha\", \"species\": \"cat\", \"breed\": \"Siamese Mix\", \"bio\": \"Chatty and affectionate! Mocha will tell you all about her day while you enjoy your latte.\", \"sex\": \"female\", \"birthYear\": 2022, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$CAT_CAFE_ID\"}")
echo "Created Mocha - Cat Cafe siamese mix"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"name\": \"Biscuit\", \"species\": \"cat\", \"breed\": \"Orange Tabby\", \"bio\": \"The cafe mascot! Biscuit greets everyone and makes the best air biscuits.\", \"sex\": \"male\", \"birthYear\": 2021, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$CAT_CAFE_ID\"}")
echo "Created Biscuit - Cat Cafe orange tabby"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"name\": \"Luna\", \"species\": \"cat\", \"breed\": \"Tuxedo\", \"bio\": \"Elegant lady who prefers watching from her perch. Perfect for someone who appreciates a cat with boundaries.\", \"sex\": \"female\", \"birthYear\": 2020, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$CAT_CAFE_ID\"}")
echo "Created Luna - Cat Cafe tuxedo"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"name\": \"Ginger\", \"species\": \"cat\", \"breed\": \"Persian\", \"bio\": \"Fluffy cloud of love! Requires daily brushing but worth every minute.\", \"sex\": \"female\", \"birthYear\": 2019, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$CAT_CAFE_ID\"}")
echo "Created Ginger - Cat Cafe persian"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"name\": \"Midnight\", \"species\": \"cat\", \"breed\": \"Black Shorthair\", \"bio\": \"Black cats are magical! Midnight brings good luck and endless cuddles.\", \"sex\": \"male\", \"birthYear\": 2023, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$CAT_CAFE_ID\"}")
echo "Created Midnight - Cat Cafe black cat"

# Scales & Tails Reptile Shop (Diana manages)
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"name\": \"Sunny\", \"species\": \"reptile\", \"breed\": \"Leopard Gecko\", \"bio\": \"Beautiful tangerine morph! Captive bred and eating great.\", \"sex\": \"female\", \"birthYear\": 2024, \"petStatus\": \"for_sale\", \"actingAsOrgId\": \"$REPTILE_SHOP_ID\"}")
echo "Created Sunny - Reptile Shop leopard gecko"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"name\": \"Blaze\", \"species\": \"reptile\", \"breed\": \"Corn Snake\", \"bio\": \"Stunning blood red corn snake. Perfect beginner snake, very docile.\", \"sex\": \"male\", \"birthYear\": 2024, \"petStatus\": \"for_sale\", \"actingAsOrgId\": \"$REPTILE_SHOP_ID\"}")
echo "Created Blaze - Reptile Shop corn snake"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"name\": \"Marble\", \"species\": \"reptile\", \"breed\": \"Leopard Gecko\", \"bio\": \"Mack snow morph with gorgeous spots. Friendly and loves to explore.\", \"sex\": \"male\", \"birthYear\": 2024, \"petStatus\": \"for_sale\", \"actingAsOrgId\": \"$REPTILE_SHOP_ID\"}")
echo "Created Marble - Reptile Shop leopard gecko"

# Bunny Bunch Rescue rabbits (Emma manages)
PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d "{\"name\": \"Thumper\", \"species\": \"small_animal\", \"breed\": \"Mini Rex\", \"bio\": \"Velvety soft fur and loves head rubs! Thumper is litter trained and apartment-friendly.\", \"sex\": \"male\", \"birthYear\": 2023, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$BUNNY_RESCUE_ID\"}")
echo "Created Thumper - Bunny Rescue mini rex"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d "{\"name\": \"Clover\", \"species\": \"small_animal\", \"breed\": \"Dutch\", \"bio\": \"Bonded with Thumper! These two must go home together. Double the binkies!\", \"sex\": \"female\", \"birthYear\": 2023, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$BUNNY_RESCUE_ID\"}")
echo "Created Clover - Bunny Rescue dutch (bonded with Thumper)"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d "{\"name\": \"Snowball\", \"species\": \"small_animal\", \"breed\": \"Lionhead\", \"bio\": \"Fluffy mane and personality to match! Snowball loves to binky and zoom.\", \"sex\": \"female\", \"birthYear\": 2024, \"petStatus\": \"for_adoption\", \"actingAsOrgId\": \"$BUNNY_RESCUE_ID\"}")
echo "Created Snowball - Bunny Rescue lionhead"

PET_RESPONSE=$(curl -s -X POST "$API_URL/pets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d "{\"name\": \"Oreo\", \"species\": \"small_animal\", \"breed\": \"Holland Lop\", \"bio\": \"NEEDS HELP: Oreo needs dental surgery. These lops can have teeth issues. Help us help him!\", \"sex\": \"male\", \"birthYear\": 2022, \"petStatus\": \"needs_help\", \"actingAsOrgId\": \"$BUNNY_RESCUE_ID\"}")
echo "Created Oreo - Bunny Rescue lop needing dental work"

# ==================== FOLLOW RELATIONSHIPS ====================
echo ""
echo "=== Creating Follow Relationships ==="

# Alice follows (dog people and orgs)
curl -s -X POST "$API_URL/follow/user/$FRANK_ID" -H "Authorization: Bearer $ALICE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$EMMA_ID" -H "Authorization: Bearer $ALICE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$HENRY_ID" -H "Authorization: Bearer $ALICE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$DUKE_ID" -H "Authorization: Bearer $ALICE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$BUDDY_ID" -H "Authorization: Bearer $ALICE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$SHELTER_ID" -H "Authorization: Bearer $ALICE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$BREEDER_ID" -H "Authorization: Bearer $ALICE_TOKEN" > /dev/null
echo "Alice follows: Frank, Emma, Henry, Duke, Buddy, Happy Paws Shelter, Golden Dreams Kennel"

# Bob follows (cat lovers and rescues)
curl -s -X POST "$API_URL/follow/user/$EMMA_ID" -H "Authorization: Bearer $BOB_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$GRACE_ID" -H "Authorization: Bearer $BOB_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$MITTENS_ID" -H "Authorization: Bearer $BOB_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$VET_ID" -H "Authorization: Bearer $BOB_TOKEN" > /dev/null
echo "Bob follows: Emma, Dr. Grace, Mittens, City Pet Hospital"

# Charlie follows (bird community)
curl -s -X POST "$API_URL/follow/user/$EMMA_ID" -H "Authorization: Bearer $CHARLIE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$DIANA_ID" -H "Authorization: Bearer $CHARLIE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$CAPTAIN_ID" -H "Authorization: Bearer $CHARLIE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$RESCUE_ID" -H "Authorization: Bearer $CHARLIE_TOKEN" > /dev/null
echo "Charlie follows: Emma, Diana, Captain, Exotic Rescue Foundation"

# Diana follows (reptile and exotic community)
curl -s -X POST "$API_URL/follow/user/$CHARLIE_ID" -H "Authorization: Bearer $DIANA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$GRACE_ID" -H "Authorization: Bearer $DIANA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$COCO_ID" -H "Authorization: Bearer $DIANA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$VET_ID" -H "Authorization: Bearer $DIANA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$BIRD_RESCUE_ID" -H "Authorization: Bearer $DIANA_TOKEN" > /dev/null
echo "Diana follows: Charlie, Dr. Grace, Coco, City Pet Hospital, Feathered Friends Sanctuary"

# Emma follows (everyone - social butterfly)
curl -s -X POST "$API_URL/follow/user/$ALICE_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$BOB_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$CHARLIE_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$DIANA_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$GRACE_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$MAX_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$BELLA_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$COCO_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$DRACO_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$SHELTER_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$RESCUE_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$VET_ID" -H "Authorization: Bearer $EMMA_TOKEN" > /dev/null
echo "Emma follows: Alice, Bob, Charlie, Diana, Dr. Grace, Max, Bella, Coco, Draco, + 3 orgs"

# Frank follows (breeding community)
curl -s -X POST "$API_URL/follow/user/$ALICE_ID" -H "Authorization: Bearer $FRANK_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$GRACE_ID" -H "Authorization: Bearer $FRANK_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$HENRY_ID" -H "Authorization: Bearer $FRANK_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$BUDDY_ID" -H "Authorization: Bearer $FRANK_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$VET_ID" -H "Authorization: Bearer $FRANK_TOKEN" > /dev/null
echo "Frank follows: Alice, Dr. Grace, Henry, Buddy, City Pet Hospital"

# Grace follows (vet professional network)
curl -s -X POST "$API_URL/follow/user/$ALICE_ID" -H "Authorization: Bearer $GRACE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$BOB_ID" -H "Authorization: Bearer $GRACE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$CHARLIE_ID" -H "Authorization: Bearer $GRACE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$DIANA_ID" -H "Authorization: Bearer $GRACE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$SHELTER_ID" -H "Authorization: Bearer $GRACE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$RESCUE_ID" -H "Authorization: Bearer $GRACE_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$BIRD_RESCUE_ID" -H "Authorization: Bearer $GRACE_TOKEN" > /dev/null
echo "Dr. Grace follows: Alice, Bob, Charlie, Diana, + 3 rescue orgs"

# Henry follows (learning from everyone)
curl -s -X POST "$API_URL/follow/user/$ALICE_ID" -H "Authorization: Bearer $HENRY_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$FRANK_ID" -H "Authorization: Bearer $HENRY_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/user/$GRACE_ID" -H "Authorization: Bearer $HENRY_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$MAX_ID" -H "Authorization: Bearer $HENRY_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/pet/$BELLA_ID" -H "Authorization: Bearer $HENRY_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$BREEDER_ID" -H "Authorization: Bearer $HENRY_TOKEN" > /dev/null
curl -s -X POST "$API_URL/follow/organization/$VET_ID" -H "Authorization: Bearer $HENRY_TOKEN" > /dev/null
echo "Henry follows: Alice, Frank, Dr. Grace, Max, Bella, Golden Dreams, City Pet Hospital"

# ==================== POSTS ====================
echo ""
echo "=== Creating Posts ==="

# Alice's posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"content\": \"Just finished an amazing training session with Max! After 6 years together, he still surprises me with how fast he learns new tricks. Today we mastered 'play dead' - his dramatic flair is Oscar-worthy!\", \"targetUserId\": \"$ALICE_ID\"}" > /dev/null
echo "Alice posted on her wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"content\": \"Max visited the children's hospital today. The kids' faces light up every time they see him. This is why I became a dog trainer - moments like these.\", \"petId\": \"$MAX_ID\"}" > /dev/null
echo "Alice posted on Max's wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"content\": \"HUGE NEWS! Bella just qualified for nationals in agility! All those early morning training sessions paid off. So proud of my girl!\", \"petId\": \"$BELLA_ID\"}" > /dev/null
echo "Alice posted on Bella's wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -d "{\"content\": \"Rocky is having a good hip day today. Took him for a gentle walk around the block. He may be retired but he still has that K9 alertness - spotted a squirrel from 50 yards away!\", \"petId\": \"$ROCKY_ID\"}" > /dev/null
echo "Alice posted on Rocky's wall"

# Bob's posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"content\": \"Foster fail alert! After 3 months of fostering, I officially adopted Whiskers. Who am I kidding, he chose me the first day he walked in.\", \"targetUserId\": \"$BOB_ID\"}" > /dev/null
echo "Bob posted on his wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"content\": \"Whiskers knocked over my coffee this morning, looked me dead in the eyes, and started grooming himself. The audacity. The confidence. I respect it.\", \"petId\": \"$WHISKERS_ID\"}" > /dev/null
echo "Bob posted on Whiskers' wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"content\": \"Shadow has discovered that the bathroom has excellent acoustics for 3 AM yowling concerts. Send coffee.\", \"petId\": \"$SHADOW_ID\"}" > /dev/null
echo "Bob posted on Shadow's wall"

# Charlie's posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d "{\"content\": \"25 years ago today, I brought home a featherless baby cockatoo. Today Coco woke me up by saying 'coffee time!' in my own voice. Best decision I ever made.\", \"targetUserId\": \"$CHARLIE_ID\"}" > /dev/null
echo "Charlie posted on his wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d "{\"content\": \"Coco learned to imitate the microwave beep. Now she beeps at random intervals and laughs when I check it. I've created a monster.\", \"petId\": \"$COCO_ID\"}" > /dev/null
echo "Charlie posted on Coco's wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d "{\"content\": \"Mango discovered his reflection today. He's been serenading 'the handsome stranger' for 3 hours. The ego on this bird!\", \"petId\": \"$MANGO_ID\"}" > /dev/null
echo "Charlie posted on Mango's wall"

# Diana's posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"content\": \"Just finished a school presentation with Draco. A kid asked if dinosaurs are just really old lizards. These are the questions that keep me going!\", \"targetUserId\": \"$DIANA_ID\"}" > /dev/null
echo "Diana posted on her wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"content\": \"Draco has learned that head bobbing gets him extra treats. He now bobs at everyone. My little manipulator.\", \"petId\": \"$DRACO_ID\"}" > /dev/null
echo "Diana posted on Draco's wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"content\": \"Medusa shed her skin in one perfect piece! Healthy girl. Fun fact: ball pythons can live 30+ years with proper care!\", \"petId\": \"$MEDUSA_ID\"}" > /dev/null
echo "Diana posted on Medusa's wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"content\": \"Sheldon's 10 year rescue-versary! From a sad carnival prize to a thriving 200-gallon king. Never buy turtles from carnivals, folks. Adopt, don't shop!\", \"petId\": \"$SHELDON_ID\"}" > /dev/null
echo "Diana posted on Sheldon's wall"

# Emma's posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d "{\"content\": \"Morning roll call: Duke tried to fit on my lap. Peanut is barking at nothing. Mittens is judging everyone. Bun Bun is zooming. Captain is narrating the chaos. Just another Tuesday!\", \"targetUserId\": \"$EMMA_ID\"}" > /dev/null
echo "Emma posted on her wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d "{\"content\": \"Duke sat on Peanut (gently). Peanut was NOT amused. The size difference in this house is comedy gold.\", \"petId\": \"$DUKE_ID\"}" > /dev/null
echo "Emma posted on Duke's wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d "{\"content\": \"Bun Bun and Mittens cuddling session! The interspecies friendship I didn't know I needed. They groom each other and it's the cutest thing ever.\", \"petId\": \"$BUNBUN_ID\"}" > /dev/null
echo "Emma posted on Bun Bun's wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EMMA_TOKEN" \
  -d "{\"content\": \"Captain learned to perfectly imitate Duke's bark. Duke is very confused. Captain is very smug.\", \"petId\": \"$CAPTAIN_ID\"}" > /dev/null
echo "Emma posted on Captain's wall"

# Frank's posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FRANK_TOKEN" \
  -d "{\"content\": \"Health testing results are in for our new litter! All puppies cleared for hips, elbows, heart, and eyes. This is why ethical breeding matters.\", \"targetUserId\": \"$FRANK_ID\"}" > /dev/null
echo "Frank posted on his wall"

# Grace's posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRACE_TOKEN" \
  -d "{\"content\": \"PSA: With summer coming, remember that hot pavement can burn your pet's paws! If it's too hot for your hand, it's too hot for their feet. Walk early morning or late evening.\", \"targetUserId\": \"$GRACE_ID\"}" > /dev/null
echo "Dr. Grace posted on her wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRACE_TOKEN" \
  -d "{\"content\": \"Successfully saved a senior cat last night who ate a lily. Reminder: lilies are EXTREMELY toxic to cats! Even the pollen can cause kidney failure. Keep them out of your house if you have cats.\", \"targetUserId\": \"$GRACE_ID\"}" > /dev/null
echo "Dr. Grace posted health warning"

# Henry's posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $HENRY_TOKEN" \
  -d "{\"content\": \"First week with Buddy: 3 shoes destroyed, 1 potty accident, 47 cuddles, and my heart completely stolen. Worth it.\", \"targetUserId\": \"$HENRY_ID\"}" > /dev/null
echo "Henry posted on his wall"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $HENRY_TOKEN" \
  -d "{\"content\": \"Buddy graduated puppy kindergarten today! He was... enthusiastic. The trainer said he has 'a lot of personality.' I choose to take that as a compliment.\", \"petId\": \"$BUDDY_ID\"}" > /dev/null
echo "Henry posted on Buddy's wall"

# Organization posts
echo ""
echo "=== Creating Organization Posts ==="

# Happy Paws Shelter posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"content\": \"ADOPTION EVENT this Saturday! All adoption fees waived for senior pets (7+). Help us find homes for our golden oldies. They have so much love to give!\", \"actingAsOrgId\": \"$SHELTER_ID\", \"targetOrgId\": \"$SHELTER_ID\"}" > /dev/null
echo "Happy Paws Shelter posted event"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"content\": \"Meet Luna! This gorgeous husky mix is looking for an active family. She loves hiking, running, and making friends at the dog park.\", \"actingAsOrgId\": \"$SHELTER_ID\", \"petId\": \"$LUNA_ID\"}" > /dev/null
echo "Happy Paws posted about Luna"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"content\": \"Update on Bonnie & Clyde: Still waiting for their forever home! This bonded pair is house trained, crate trained, and full of love. They must be adopted together!\", \"actingAsOrgId\": \"$SHELTER_ID\", \"targetOrgId\": \"$SHELTER_ID\"}" > /dev/null
echo "Happy Paws posted bonded pair update"

# Shelter posting to Henry's wall (welcoming new adopter)
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -d "{\"content\": \"Welcome to the pet parent club, Henry! So happy to see Buddy from Golden Dreams is thriving with you. Don't forget - we offer free training resources for new pet owners!\", \"actingAsOrgId\": \"$SHELTER_ID\", \"targetUserId\": \"$HENRY_ID\"}" > /dev/null
echo "Happy Paws posted to Henry's wall"

# Exotic Rescue Foundation posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"content\": \"URGENT: Rex the tegu needs your help! He came to us severely malnourished with metabolic bone disease. Vet bills are mounting. Can you help sponsor his recovery?\", \"actingAsOrgId\": \"$RESCUE_ID\", \"petId\": \"$REX_ID\"}" > /dev/null
echo "Exotic Rescue posted about Rex"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DIANA_TOKEN" \
  -d "{\"content\": \"Reptile care myth: Reptiles don't need enrichment. WRONG! Slinky here loves exploring new hides and climbing branches. Mental stimulation is important for all pets!\", \"actingAsOrgId\": \"$RESCUE_ID\", \"petId\": \"$SLINKY_ID\"}" > /dev/null
echo "Exotic Rescue posted educational content"

# Golden Dreams Kennel posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FRANK_TOKEN" \
  -d "{\"content\": \"NEW LITTER ANNOUNCEMENT! Our champion girl Sunshine had 8 beautiful puppies. All spoken for, but join our waiting list for future litters!\", \"actingAsOrgId\": \"$BREEDER_ID\", \"targetOrgId\": \"$BREEDER_ID\"}" > /dev/null
echo "Golden Dreams posted litter announcement"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FRANK_TOKEN" \
  -d "{\"content\": \"Goldie is ready for her forever home! She's smart, gentle, and already knows basic commands. Perfect for families with children.\", \"actingAsOrgId\": \"$BREEDER_ID\", \"petId\": \"$GOLDIE_ID\"}" > /dev/null
echo "Golden Dreams posted about Goldie"

# Breeder posting to Alice's wall (professional connection)
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FRANK_TOKEN" \
  -d "{\"content\": \"Alice, loved seeing your agility work with Bella! Would you consider doing a training demo at our puppy meet-and-greet next month?\", \"actingAsOrgId\": \"$BREEDER_ID\", \"targetUserId\": \"$ALICE_ID\"}" > /dev/null
echo "Golden Dreams posted to Alice's wall"

# City Pet Hospital posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRACE_TOKEN" \
  -d "{\"content\": \"Reminder: Heartworm prevention is year-round, even in cooler months! Schedule your pet's annual checkup and stay protected.\", \"actingAsOrgId\": \"$VET_ID\", \"targetOrgId\": \"$VET_ID\"}" > /dev/null
echo "City Pet Hospital posted reminder"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRACE_TOKEN" \
  -d "{\"content\": \"We now offer acupuncture and physical therapy for pets! Great for senior pets like Rocky (shoutout to Alice!) dealing with mobility issues.\", \"actingAsOrgId\": \"$VET_ID\", \"targetOrgId\": \"$VET_ID\"}" > /dev/null
echo "City Pet Hospital posted new services"

# Vet posting to Diana's wall (professional collaboration)
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $GRACE_TOKEN" \
  -d "{\"content\": \"Diana, Rex is making great progress! His calcium levels are improving. Thank you for the rescue work you do - these animals deserve a second chance.\", \"actingAsOrgId\": \"$VET_ID\", \"targetUserId\": \"$DIANA_ID\"}" > /dev/null
echo "City Pet Hospital posted to Diana's wall"

# Feathered Friends Sanctuary posts
curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d "{\"content\": \"Einstein turned 40 today! He's been at the sanctuary for 15 years and is our most beloved permanent resident. Parrots can live for decades - please research before adopting!\", \"actingAsOrgId\": \"$BIRD_RESCUE_ID\", \"petId\": \"$EINSTEIN_ID\"}" > /dev/null
echo "Feathered Friends posted about Einstein"

curl -s -X POST "$API_URL/posts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $CHARLIE_TOKEN" \
  -d "{\"content\": \"Pepper is finally stepping up! After months of rehabilitation, this beautiful macaw is learning to trust humans again. Looking for an experienced parrot guardian.\", \"actingAsOrgId\": \"$BIRD_RESCUE_ID\", \"petId\": \"$PEPPER_ID\"}" > /dev/null
echo "Feathered Friends posted about Pepper"

echo ""
echo "=== Seeding Complete ==="
echo ""
echo "Test accounts created:"
echo "  - alice@test.com / password123 - Dog trainer with Max, Bella, Rocky (manages Paws & Claws Rescue)"
echo "  - bob@test.com / password123 - Cat lover with Whiskers, Shadow (manages Happy Paws Shelter, Whisker Haven Cat Cafe)"
echo "  - charlie@test.com / password123 - Bird vet with Coco, Mango (manages Feathered Friends Sanctuary)"
echo "  - diana@test.com / password123 - Herpetologist with Draco, Medusa, Sheldon (manages Exotic Rescue Foundation, Scales & Tails)"
echo "  - emma@test.com / password123 - Multi-pet owner with Duke, Peanut, Mittens, Bun Bun, Captain (manages Bunny Bunch Rescue)"
echo "  - frank@test.com / password123 - Breeder (manages Golden Dreams Kennel)"
echo "  - grace@test.com / password123 - Veterinarian (manages City Pet Hospital, Mountain View Animal Hospital)"
echo "  - henry@test.com / password123 - New pet owner with Buddy"
echo ""
echo "Organizations (10 total):"
echo "  - Happy Paws Shelter - Has Luna, Oliver, Bonnie, Clyde for adoption"
echo "  - Exotic Rescue Foundation - Has Spike, Slinky for adoption, Rex needs help"
echo "  - Golden Dreams Kennel - Has Goldie, Scout for sale"
echo "  - City Pet Hospital - Vet clinic"
echo "  - Feathered Friends Sanctuary - Has Einstein, Pepper"
echo "  - Paws & Claws Rescue - Has Daisy, Thor, Ziggy for adoption, Rosie needs help"
echo "  - Whisker Haven Cat Cafe - Has Mocha, Biscuit, Luna, Ginger, Midnight for adoption"
echo "  - Scales & Tails Reptile Shop - Has Sunny, Blaze, Marble for sale"
echo "  - Mountain View Animal Hospital - Vet clinic"
echo "  - Bunny Bunch Rescue - Has Thumper, Clover, Snowball for adoption, Oreo needs help"
echo ""
echo "Total: 8 users, 10 organizations, 40+ pets"
echo ""
