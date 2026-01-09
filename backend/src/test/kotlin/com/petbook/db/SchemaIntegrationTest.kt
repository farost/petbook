package com.petbook.db

import kotlin.test.*

/**
 * Integration tests for the Petbook TypeDB schema.
 *
 * These tests:
 * 1. Create a fresh test database (petbook_test)
 * 2. Load the schema
 * 3. Verify entities, relations, and attributes exist
 * 4. Test basic insert/query operations
 * 5. Clean up the test database
 *
 * Prerequisites:
 * - TypeDB Cloud credentials in .env file
 * - Network access to TypeDB Cloud
 *
 * Run with: ./gradlew test --tests "com.petbook.db.SchemaIntegrationTest"
 */
class SchemaIntegrationTest {

    companion object {
        private const val SCHEMA_PATH = "../schema/petbook.tql"
    }

    private lateinit var helper: TypeDBTestHelper

    @BeforeTest
    fun setup() {
        try {
            helper = TypeDBTestHelper.create("petbook")
            helper.loadSchema(SCHEMA_PATH)
        } catch (e: Exception) {
            fail("Failed to set up test database: ${e.message}")
        }
    }

    @AfterTest
    fun teardown() {
        if (::helper.isInitialized) {
            helper.close()
        }
    }

    // =========================================================================
    // Schema Structure Tests
    // =========================================================================

    @Test
    fun `schema loads without errors`() {
        // If we get here, schema loaded successfully in setup
        assertTrue(helper.testDatabaseName.endsWith("_test"))
    }

    @Test
    fun `entity types exist - user hierarchy`() {
        assertTrue(helper.entityTypeExists("user"), "user type should exist")
        assertTrue(helper.entityTypeExists("individual"), "individual type should exist")
        assertTrue(helper.entityTypeExists("organization"), "organization type should exist")
    }

    @Test
    fun `entity types exist - pet`() {
        assertTrue(helper.entityTypeExists("pet"), "pet type should exist")
    }

    @Test
    fun `entity types exist - content`() {
        assertTrue(helper.entityTypeExists("post"), "post type should exist")
        assertTrue(helper.entityTypeExists("photo"), "photo type should exist")
    }

    @Test
    fun `relation types exist`() {
        assertTrue(helper.relationTypeExists("ownership"), "ownership relation should exist")
        assertTrue(helper.relationTypeExists("follows"), "follows relation should exist")
        assertTrue(helper.relationTypeExists("authorship"), "authorship relation should exist")
        assertTrue(helper.relationTypeExists("pet_post"), "pet_post relation should exist")
        assertTrue(helper.relationTypeExists("reaction"), "reaction relation should exist")
        assertTrue(helper.relationTypeExists("adoption_listing"), "adoption_listing relation should exist")
    }

    @Test
    fun `attribute types exist - common`() {
        assertTrue(helper.attributeTypeExists("id"), "id attribute should exist")
        assertTrue(helper.attributeTypeExists("name"), "name attribute should exist")
        assertTrue(helper.attributeTypeExists("email"), "email attribute should exist")
        assertTrue(helper.attributeTypeExists("bio"), "bio attribute should exist")
    }

    @Test
    fun `attribute types exist - pet specific`() {
        assertTrue(helper.attributeTypeExists("species"), "species attribute should exist")
        assertTrue(helper.attributeTypeExists("breed"), "breed attribute should exist")
        assertTrue(helper.attributeTypeExists("pet_status"), "pet_status attribute should exist")
    }

    @Test
    fun `attribute types exist - ownership`() {
        assertTrue(helper.attributeTypeExists("ownership_status"), "ownership_status attribute should exist")
        assertTrue(helper.attributeTypeExists("start_date"), "start_date attribute should exist")
        assertTrue(helper.attributeTypeExists("end_date"), "end_date attribute should exist")
    }

    // =========================================================================
    // Data Operation Tests
    // =========================================================================

    @Test
    fun `can insert and query individual user`() {
        // Insert a user
        helper.writeQuery("""
            insert
            ${"$"}user isa individual,
                has id "user-001",
                has email "test@example.com",
                has name "Test User";
        """.trimIndent())

        // Query the user
        val exists = helper.queryHasResults("""
            match
            ${"$"}user isa individual, has id "user-001";
            select ${"$"}user;
        """.trimIndent())

        assertTrue(exists, "Should find the inserted user")
    }

    @Test
    fun `can insert and query pet`() {
        // Insert a pet
        helper.writeQuery("""
            insert
            ${"$"}pet isa pet,
                has id "pet-001",
                has name "Fluffy",
                has species "cat",
                has pet_status "owned";
        """.trimIndent())

        // Query the pet
        val exists = helper.queryHasResults("""
            match
            ${"$"}pet isa pet, has name "Fluffy";
            select ${"$"}pet;
        """.trimIndent())

        assertTrue(exists, "Should find the inserted pet")
    }

    @Test
    fun `can create ownership relation`() {
        // Insert user and pet first
        helper.writeQuery("""
            insert
            ${"$"}user isa individual,
                has id "user-002",
                has email "owner@example.com",
                has name "Pet Owner";
            ${"$"}pet isa pet,
                has id "pet-002",
                has name "Buddy",
                has species "dog";
        """.trimIndent())

        // Insert ownership relation separately
        helper.writeQuery("""
            match
            ${"$"}user isa individual, has id "user-002";
            ${"$"}pet isa pet, has id "pet-002";
            insert
            (owner: ${"$"}user, pet: ${"$"}pet) isa ownership,
                has ownership_status "current";
        """.trimIndent())

        // Query the ownership
        val exists = helper.queryHasResults("""
            match
            ${"$"}ownership (owner: ${"$"}user, pet: ${"$"}pet) isa ownership,
                has ownership_status "current";
            ${"$"}pet has name "Buddy";
            select ${"$"}user, ${"$"}pet;
        """.trimIndent())

        assertTrue(exists, "Should find the ownership relation")
    }

    @Test
    fun `can create follows relation between users`() {
        // Insert two users
        helper.writeQuery("""
            insert
            ${"$"}user1 isa individual,
                has id "user-003",
                has email "user1@example.com",
                has name "User One";
            ${"$"}user2 isa individual,
                has id "user-004",
                has email "user2@example.com",
                has name "User Two";
            (follower: ${"$"}user1, following: ${"$"}user2) isa follows;
        """.trimIndent())

        // Query the follows relation
        val exists = helper.queryHasResults("""
            match
            ${"$"}follows (follower: ${"$"}follower, following: ${"$"}following) isa follows;
            ${"$"}follower has name "User One";
            ${"$"}following has name "User Two";
            select ${"$"}follower, ${"$"}following;
        """.trimIndent())

        assertTrue(exists, "Should find the follows relation")
    }

    @Test
    fun `can create follows relation to pet`() {
        // Insert user and pet, then follow
        helper.writeQuery("""
            insert
            ${"$"}user isa individual,
                has id "user-005",
                has email "petlover@example.com",
                has name "Pet Lover";
            ${"$"}pet isa pet,
                has id "pet-003",
                has name "Whiskers",
                has species "cat";
            (follower: ${"$"}user, following: ${"$"}pet) isa follows;
        """.trimIndent())

        // Query the follows relation to pet
        val exists = helper.queryHasResults("""
            match
            ${"$"}follows (follower: ${"$"}user, following: ${"$"}pet) isa follows;
            ${"$"}pet isa pet, has name "Whiskers";
            select ${"$"}user, ${"$"}pet;
        """.trimIndent())

        assertTrue(exists, "Should find user following a pet")
    }

    @Test
    fun `organization can create adoption listing`() {
        // Insert shelter and pet with adoption listing
        helper.writeQuery("""
            insert
            ${"$"}shelter isa organization,
                has id "org-001",
                has email "shelter@example.com",
                has name "Happy Paws Shelter",
                has user_type "shelter";
            ${"$"}pet isa pet,
                has id "pet-004",
                has name "Max",
                has species "dog",
                has pet_status "needs_adoption";
            (shelter: ${"$"}shelter, pet: ${"$"}pet) isa adoption_listing,
                has listing_status "active",
                has adoption_fee 150.0,
                has adoption_description "Friendly dog looking for a home";
        """.trimIndent())

        // Query the adoption listing
        val exists = helper.queryHasResults("""
            match
            ${"$"}listing (shelter: ${"$"}shelter, pet: ${"$"}pet) isa adoption_listing,
                has listing_status "active";
            ${"$"}pet has name "Max";
            select ${"$"}shelter, ${"$"}pet;
        """.trimIndent())

        assertTrue(exists, "Should find the adoption listing")
    }
}
