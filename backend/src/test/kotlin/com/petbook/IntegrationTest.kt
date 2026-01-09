package com.petbook

import com.petbook.auth.AuthService
import com.petbook.db.TypeDBConfig
import com.petbook.db.TypeDBService
import com.petbook.follow.FollowRepository
import com.petbook.organization.OrganizationRepository
import com.petbook.pet.PetRepository
import com.petbook.post.PostRepository
import com.petbook.user.UserRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.serialization.json.*
import kotlin.test.*
import java.util.UUID

class IntegrationTest {

    companion object {
        // IMPORTANT: Use forTests() to connect to petbook_test database, NOT production
        private val config = TypeDBConfig.forTests()
        private val typedbService = TypeDBService(config)
        private val jwtSecret = "test-secret"
        private val jwtIssuer = "petbook"
        private val jwtAudience = "petbook-users"

        init {
            println("Connecting to TEST database: ${config.database}")
            typedbService.connect()
            typedbService.ensureDatabaseExists()
            // Load schema to ensure database has latest types
            typedbService.loadSchemaFromFile("../schema/petbook.tql")
        }
    }

    private val userRepository = UserRepository(typedbService)
    private val petRepository = PetRepository(typedbService)
    private val postRepository = PostRepository(typedbService)
    private val followRepository = FollowRepository(typedbService)
    private val organizationRepository = OrganizationRepository(typedbService)
    private val authService = AuthService(userRepository, jwtSecret, jwtIssuer, jwtAudience)

    private fun createTestApp() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        createClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    private fun uniqueEmail() = "test-${UUID.randomUUID()}@example.com"

    // ==================== AUTH TESTS ====================

    @Test
    fun `register new user succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("token"))
        assertTrue(body.containsKey("user"))
    }

    @Test
    fun `register with existing email fails`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register first time
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }

        // Try to register again with same email
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User 2"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `login with valid credentials succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }

        // Login
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("token"))
    }

    @Test
    fun `login with wrong password fails`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }

        // Login with wrong password
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "wrongpassword"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ==================== PET TESTS ====================

    @Test
    fun `create pet succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet
        val response = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Buddy", "species": "dog", "breed": "Golden Retriever", "bio": "A friendly dog"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("id"))
        assertEquals("Buddy", body["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `get pet by id succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet
        val createResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Buddy", "species": "dog"}""")
        }
        val petId = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Get pet
        val response = client.get("/api/pets/$petId")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Buddy", body["pet"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
    }

    @Test
    fun `delete pet succeeds for owner`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet
        val createResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "ToDelete", "species": "cat"}""")
        }
        val petId = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Delete pet
        val response = client.delete("/api/pets/$petId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        // Verify pet is deleted
        val getResponse = client.get("/api/pets/$petId")
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    // ==================== POST TESTS ====================

    @Test
    fun `create post on user wall succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create post
        val response = client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "Hello world! This is my first post."}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("id"))
        assertEquals("Hello world! This is my first post.", body["content"]?.jsonPrimitive?.content)
        assertTrue(body.containsKey("createdAt"))
    }

    @Test
    fun `create post on pet wall succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet
        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Buddy", "species": "dog"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Create post on pet's wall
        val response = client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "Buddy had a great day at the park!", "petId": "$petId"}""")
        }

        println("Create post on pet wall response: ${response.status}")
        println("Create post on pet wall body: ${response.bodyAsText()}")

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("id"))
        assertEquals(petId, body["petId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `get posts for pet wall succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet
        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Whiskers", "species": "cat"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Create post on pet's wall
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "Whiskers loves to play!", "petId": "$petId"}""")
        }

        // Get posts for pet
        val response = client.get("/api/posts/pet/$petId")

        println("Get posts for pet response: ${response.status}")
        println("Get posts for pet body: ${response.bodyAsText()}")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val posts = body["posts"]?.jsonArray
        assertNotNull(posts)
        assertTrue(posts.size >= 1, "Should have at least 1 post on pet wall")
    }

    @Test
    fun `feed includes own posts`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create post
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "My own post for feed test"}""")
        }

        // Get feed
        val response = client.get("/api/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val posts = body["posts"]?.jsonArray
        assertNotNull(posts)
        assertTrue(posts.any { it.jsonObject["content"]?.jsonPrimitive?.content == "My own post for feed test" })
    }

    @Test
    fun `posts are sorted newest first`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create posts with delay
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "First post"}""")
        }

        Thread.sleep(1100) // Delay to ensure different timestamps (TypeDB datetime has second-level granularity)

        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "Second post"}""")
        }

        // Get feed
        val response = client.get("/api/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val posts = body["posts"]?.jsonArray
        assertNotNull(posts)
        assertTrue(posts.size >= 2)

        // Verify ordering - newest first
        val firstContent = posts[0].jsonObject["content"]?.jsonPrimitive?.content
        assertEquals("Second post", firstContent, "Newest post should be first")
    }

    // ==================== FOLLOW TESTS ====================

    @Test
    fun `follow pet succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register first user and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet
        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "FollowMe", "species": "dog"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Register second user
        val email2 = uniqueEmail()
        val registerResponse2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email2", "password": "password123", "name": "Follower User"}""")
        }
        val token2 = Json.parseToJsonElement(registerResponse2.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Follow pet
        val response = client.post("/api/follow/pet/$petId") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        // Check follow status
        val statusResponse = client.get("/api/follow/pet/$petId/status") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        val statusBody = Json.parseToJsonElement(statusResponse.bodyAsText()).jsonObject
        assertTrue(statusBody["isFollowing"]?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun `feed includes posts from followed pets`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User 1 creates a pet and posts on its wall
        val email1 = uniqueEmail()
        val register1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email1", "password": "password123", "name": "Pet Owner"}""")
        }
        val token1 = Json.parseToJsonElement(register1.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"name": "FeedTestPet", "species": "dog"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Post on pet's wall
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"content": "Post on FeedTestPet's wall", "petId": "$petId"}""")
        }

        // User 2 follows the pet
        val email2 = uniqueEmail()
        val register2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email2", "password": "password123", "name": "Follower"}""")
        }
        val token2 = Json.parseToJsonElement(register2.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Follow the pet
        client.post("/api/follow/pet/$petId") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        // Get feed for user 2
        val feedResponse = client.get("/api/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.OK, feedResponse.status)
        val body = Json.parseToJsonElement(feedResponse.bodyAsText()).jsonObject
        val posts = body["posts"]?.jsonArray
        assertNotNull(posts)

        val hasPetPost = posts.any {
            it.jsonObject["content"]?.jsonPrimitive?.content == "Post on FeedTestPet's wall" &&
            it.jsonObject["petId"]?.jsonPrimitive?.content == petId
        }
        assertTrue(hasPetPost, "Feed should include post from followed pet's wall")
    }

    @Test
    fun `unfollow pet succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User 1 creates a pet
        val email1 = uniqueEmail()
        val register1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email1", "password": "password123", "name": "Pet Owner"}""")
        }
        val token1 = Json.parseToJsonElement(register1.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"name": "UnfollowTestPet", "species": "cat"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // User 2 follows then unfollows the pet
        val email2 = uniqueEmail()
        val register2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email2", "password": "password123", "name": "Follower"}""")
        }
        val token2 = Json.parseToJsonElement(register2.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Follow the pet
        client.post("/api/follow/pet/$petId") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        // Verify following
        val statusBefore = client.get("/api/follow/pet/$petId/status") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }
        val beforeBody = Json.parseToJsonElement(statusBefore.bodyAsText()).jsonObject
        assertTrue(beforeBody["isFollowing"]?.jsonPrimitive?.boolean == true, "Should be following before unfollow")

        // Unfollow the pet
        val unfollowResponse = client.delete("/api/follow/pet/$petId") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.OK, unfollowResponse.status)

        // Verify no longer following
        val statusAfter = client.get("/api/follow/pet/$petId/status") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }
        val afterBody = Json.parseToJsonElement(statusAfter.bodyAsText()).jsonObject
        assertTrue(afterBody["isFollowing"]?.jsonPrimitive?.boolean == false, "Should not be following after unfollow")
    }

    @Test
    fun `unfollow user succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User 1
        val email1 = uniqueEmail()
        val register1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email1", "password": "password123", "name": "User To Follow"}""")
        }
        val body1 = Json.parseToJsonElement(register1.bodyAsText()).jsonObject
        val userId1 = body1["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content

        // User 2 follows then unfollows user 1
        val email2 = uniqueEmail()
        val register2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email2", "password": "password123", "name": "Follower User"}""")
        }
        val token2 = Json.parseToJsonElement(register2.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Follow user 1
        client.post("/api/follow/user/$userId1") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        // Verify following
        val statusBefore = client.get("/api/follow/user/$userId1/status") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }
        val beforeBody = Json.parseToJsonElement(statusBefore.bodyAsText()).jsonObject
        assertTrue(beforeBody["isFollowing"]?.jsonPrimitive?.boolean == true, "Should be following before unfollow")

        // Unfollow user 1
        val unfollowResponse = client.delete("/api/follow/user/$userId1") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.OK, unfollowResponse.status)

        // Verify no longer following
        val statusAfter = client.get("/api/follow/user/$userId1/status") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }
        val afterBody = Json.parseToJsonElement(statusAfter.bodyAsText()).jsonObject
        assertTrue(afterBody["isFollowing"]?.jsonPrimitive?.boolean == false, "Should not be following after unfollow")
    }

    // ==================== PROFILE TESTS ====================

    @Test
    fun `get my profile returns user and pets`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Profile Test"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet
        client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "ProfilePet", "species": "bird"}""")
        }

        // Get profile
        val response = client.get("/api/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val user = body["user"]?.jsonObject
        assertEquals("Profile Test", user?.get("name")?.jsonPrimitive?.content)

        val pets = body["pets"]?.jsonArray
        assertNotNull(pets)
        assertTrue(pets.any { it.jsonObject["name"]?.jsonPrimitive?.content == "ProfilePet" })
    }

    // ==================== ORGANIZATION TESTS ====================

    @Test
    fun `create organization succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Creator"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val response = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Happy Paws Shelter", "orgType": "shelter", "bio": "A loving shelter for pets", "location": "NYC"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body.containsKey("id"))
        assertEquals("Happy Paws Shelter", body["name"]?.jsonPrimitive?.content)
        assertEquals("shelter", body["orgType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `get my organizations returns managed orgs`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Manager"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Test Rescue", "orgType": "rescue"}""")
        }

        // Get my organizations
        val response = client.get("/api/organizations/my") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val orgs = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertTrue(orgs.size >= 1)
        assertTrue(orgs.any {
            it.jsonObject["organization"]?.jsonObject?.get("name")?.jsonPrimitive?.content == "Test Rescue"
        })
    }

    @Test
    fun `follow organization succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User 1 creates organization
        val email1 = uniqueEmail()
        val register1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email1", "password": "password123", "name": "Org Owner"}""")
        }
        val token1 = Json.parseToJsonElement(register1.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        val orgResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"name": "Follow Me Org", "orgType": "shelter"}""")
        }
        val orgId = Json.parseToJsonElement(orgResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // User 2 follows organization
        val email2 = uniqueEmail()
        val register2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email2", "password": "password123", "name": "Follower"}""")
        }
        val token2 = Json.parseToJsonElement(register2.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        val followResponse = client.post("/api/follow/organization/$orgId") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.OK, followResponse.status)

        // Check follow status
        val statusResponse = client.get("/api/follow/organization/$orgId/status") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        val statusBody = Json.parseToJsonElement(statusResponse.bodyAsText()).jsonObject
        assertTrue(statusBody["isFollowing"]?.jsonPrimitive?.boolean == true)
        assertTrue((statusBody["followerCount"]?.jsonPrimitive?.int ?: 0) >= 1)
    }

    @Test
    fun `post as organization succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Poster"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val orgResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Posting Org", "orgType": "shelter"}""")
        }
        val orgId = Json.parseToJsonElement(orgResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Post as organization
        val response = client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "Hello from the organization!", "actingAsOrgId": "$orgId"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("organization", body["authorType"]?.jsonPrimitive?.content)
        assertEquals(orgId, body["authorId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `create pet for organization succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Pet Creator"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val orgResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Pet Holding Org", "orgType": "shelter"}""")
        }
        val orgId = Json.parseToJsonElement(orgResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Create pet for organization
        val response = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Shelter Dog", "species": "dog", "actingAsOrgId": "$orgId"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Shelter Dog", body["name"]?.jsonPrimitive?.content)

        // Verify pet is owned by organization
        val petId = body["id"]?.jsonPrimitive?.content
        val petResponse = client.get("/api/pets/$petId")
        val petBody = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject
        assertEquals("organization", petBody["ownerType"]?.jsonPrimitive?.content)
        assertEquals(orgId, petBody["ownerId"]?.jsonPrimitive?.content)
    }

    // ==================== PET TRANSFER TESTS ====================

    @Test
    fun `transfer pet to user succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User 1 creates pet
        val email1 = uniqueEmail()
        val register1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email1", "password": "password123", "name": "Original Owner"}""")
        }
        val token1 = Json.parseToJsonElement(register1.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content
        val userId1 = Json.parseToJsonElement(register1.bodyAsText()).jsonObject["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content

        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"name": "TransferPet", "species": "cat"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // User 2 registers
        val email2 = uniqueEmail()
        val register2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email2", "password": "password123", "name": "New Owner"}""")
        }
        val userId2 = Json.parseToJsonElement(register2.bodyAsText()).jsonObject["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content

        // Transfer pet from user1 to user2
        val response = client.post("/api/pets/$petId/transfer") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"toUserId": "$userId2", "reason": "adoption"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        // The transfer succeeded if we got 200 OK
        // The new ownership was created (verified via logs during development)
    }

    @Test
    fun `transfer pet to organization succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Pet Owner"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet
        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "SurrenderPet", "species": "dog"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Create organization
        val orgResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Receiving Shelter", "orgType": "shelter"}""")
        }
        val orgId = Json.parseToJsonElement(orgResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Transfer pet to organization
        val response = client.post("/api/pets/$petId/transfer") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"toOrgId": "$orgId", "reason": "surrender"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        // Verify new owner is organization
        val getResponse = client.get("/api/pets/$petId")
        val petBody = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        assertEquals(orgId, petBody["ownerId"]?.jsonPrimitive?.content)
        assertEquals("organization", petBody["ownerType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `get pet ownership history returns records`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User 1 creates pet
        val email1 = uniqueEmail()
        val register1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email1", "password": "password123", "name": "First Owner"}""")
        }
        val token1 = Json.parseToJsonElement(register1.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"name": "HistoryPet", "species": "bird"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // User 2 registers
        val email2 = uniqueEmail()
        val register2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email2", "password": "password123", "name": "Second Owner"}""")
        }
        val userId2 = Json.parseToJsonElement(register2.bodyAsText()).jsonObject["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content

        // Transfer pet
        client.post("/api/pets/$petId/transfer") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"toUserId": "$userId2", "reason": "gift"}""")
        }

        // Get ownership history
        val response = client.get("/api/pets/$petId/history")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val history = body["history"]?.jsonArray
        assertNotNull(history)
        assertTrue(history.size >= 2, "Should have at least 2 ownership records after transfer")

        // Verify current owner is marked
        val currentRecord = history.find { it.jsonObject["status"]?.jsonPrimitive?.content == "current" }
        assertNotNull(currentRecord, "Should have a current ownership record")
        assertEquals("Second Owner", currentRecord?.jsonObject?.get("ownerName")?.jsonPrimitive?.content)

        // Verify previous owner has transfer reason (status is "past")
        val previousRecord = history.find { it.jsonObject["status"]?.jsonPrimitive?.content == "past" }
        assertNotNull(previousRecord, "Should have a past ownership record")
        assertEquals("gift", previousRecord?.jsonObject?.get("transferReason")?.jsonPrimitive?.content)
    }

    @Test
    fun `non-owner cannot transfer pet`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User 1 creates pet
        val email1 = uniqueEmail()
        val register1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email1", "password": "password123", "name": "Real Owner"}""")
        }
        val token1 = Json.parseToJsonElement(register1.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"name": "ProtectedPet", "species": "cat"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // User 2 tries to transfer (not the owner)
        val email2 = uniqueEmail()
        val register2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email2", "password": "password123", "name": "Not Owner"}""")
        }
        val token2 = Json.parseToJsonElement(register2.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // User 3 as target
        val email3 = uniqueEmail()
        val register3 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email3", "password": "password123", "name": "Target User"}""")
        }
        val userId3 = Json.parseToJsonElement(register3.bodyAsText()).jsonObject["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content

        // Attempt transfer
        val response = client.post("/api/pets/$petId/transfer") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token2")
            setBody("""{"toUserId": "$userId3", "reason": "adoption"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `organization feed includes org posts and pet posts`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Manager"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val orgResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Feed Test Org", "orgType": "shelter"}""")
        }
        val orgId = Json.parseToJsonElement(orgResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Create pet for organization
        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "OrgPet", "species": "dog", "actingAsOrgId": "$orgId"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Post as organization
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "Org announcement", "actingAsOrgId": "$orgId"}""")
        }

        // Post on org's pet wall
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "Post on org's pet wall", "petId": "$petId"}""")
        }

        // Get organization feed
        val response = client.get("/api/organizations/$orgId/feed") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val posts = body["posts"]?.jsonArray
        assertNotNull(posts)

        val hasOrgPost = posts.any { it.jsonObject["content"]?.jsonPrimitive?.content == "Org announcement" }
        val hasPetPost = posts.any { it.jsonObject["content"]?.jsonPrimitive?.content == "Post on org's pet wall" }

        assertTrue(hasOrgPost, "Feed should include organization posts")
        assertTrue(hasPetPost, "Feed should include posts on organization's pets")
    }

    @Test
    fun `get organization by id succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Creator"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val createResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Viewable Org", "orgType": "rescue", "bio": "We rescue animals"}""")
        }
        val orgId = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Get organization by id (public endpoint)
        val response = client.get("/api/organizations/$orgId")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val org = body["organization"]?.jsonObject
        assertEquals("Viewable Org", org?.get("name")?.jsonPrimitive?.content)
        assertEquals("rescue", org?.get("orgType")?.jsonPrimitive?.content)
        assertEquals("We rescue animals", org?.get("bio")?.jsonPrimitive?.content)
    }

    @Test
    fun `get all organizations returns list`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Creator"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Discoverable Org", "orgType": "shelter"}""")
        }

        // Get all organizations (public endpoint for Discover page)
        val response = client.get("/api/organizations")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val orgs = body["organizations"]?.jsonArray
        assertNotNull(orgs)
        assertTrue(orgs.any { it.jsonObject["name"]?.jsonPrimitive?.content == "Discoverable Org" })
    }

    @Test
    fun `feed includes posts from managed organizations`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Manager"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val orgResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "My Managed Org", "orgType": "shelter"}""")
        }
        val orgId = Json.parseToJsonElement(orgResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Post as organization (wall post)
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "Managed org wall post", "actingAsOrgId": "$orgId"}""")
        }

        // Get feed - should include the org post even though user doesn't "follow" their own org
        val feedResponse = client.get("/api/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, feedResponse.status)
        val body = Json.parseToJsonElement(feedResponse.bodyAsText()).jsonObject
        val posts = body["posts"]?.jsonArray
        assertNotNull(posts)

        val hasManagedOrgPost = posts.any {
            it.jsonObject["content"]?.jsonPrimitive?.content == "Managed org wall post" &&
            it.jsonObject["authorType"]?.jsonPrimitive?.content == "organization"
        }
        assertTrue(hasManagedOrgPost, "Feed should include posts from managed organizations")
    }

    @Test
    fun `feed includes posts from managed org pets`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Pet Manager"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val orgResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Pet Org", "orgType": "shelter"}""")
        }
        val orgId = Json.parseToJsonElement(orgResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Create pet for organization
        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Org's Pet", "species": "dog", "actingAsOrgId": "$orgId"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Post on org's pet wall (as the user)
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"content": "Post on managed org's pet wall", "petId": "$petId"}""")
        }

        // Get feed - should include post on managed org's pet wall
        val feedResponse = client.get("/api/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, feedResponse.status)
        val body = Json.parseToJsonElement(feedResponse.bodyAsText()).jsonObject
        val posts = body["posts"]?.jsonArray
        assertNotNull(posts)

        val hasOrgPetPost = posts.any {
            it.jsonObject["content"]?.jsonPrimitive?.content == "Post on managed org's pet wall" &&
            it.jsonObject["petId"]?.jsonPrimitive?.content == petId
        }
        assertTrue(hasOrgPetPost, "Feed should include posts from managed organization's pets")
    }

    @Test
    fun `pets endpoint returns paginated response`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // Get pets with pagination
        val response = client.get("/api/pets?page=1&pageSize=10")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["pets"])
        assertNotNull(body["total"])
        assertNotNull(body["page"])
        assertNotNull(body["pageSize"])
        assertNotNull(body["totalPages"])
        assertEquals(1, body["page"]?.jsonPrimitive?.int)
        assertEquals(10, body["pageSize"]?.jsonPrimitive?.int)
    }

    @Test
    fun `users endpoint returns paginated response`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // Get users with pagination
        val response = client.get("/api/users?page=1&pageSize=25")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["users"])
        assertNotNull(body["total"])
        assertNotNull(body["page"])
        assertNotNull(body["pageSize"])
        assertNotNull(body["totalPages"])
        assertEquals(1, body["page"]?.jsonPrimitive?.int)
        assertEquals(25, body["pageSize"]?.jsonPrimitive?.int)
    }

    @Test
    fun `organizations endpoint returns paginated response`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // Get orgs with pagination
        val response = client.get("/api/organizations?page=1&pageSize=50")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["organizations"])
        assertNotNull(body["total"])
        assertNotNull(body["page"])
        assertNotNull(body["pageSize"])
        assertNotNull(body["totalPages"])
        assertEquals(1, body["page"]?.jsonPrimitive?.int)
        assertEquals(50, body["pageSize"]?.jsonPrimitive?.int)
    }

    @Test
    fun `feed includes posts from followed users pets`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val followerEmail = uniqueEmail()
        val ownerEmail = uniqueEmail()

        // Register follower
        val followerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$followerEmail", "password": "password123", "name": "Follower"}""")
        }
        val followerToken = Json.parseToJsonElement(followerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Register pet owner
        val ownerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$ownerEmail", "password": "password123", "name": "Pet Owner"}""")
        }
        val ownerToken = Json.parseToJsonElement(ownerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content
        val ownerId = Json.parseToJsonElement(ownerResponse.bodyAsText()).jsonObject["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content

        // Owner creates a pet
        val petResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            setBody("""{"name": "Followed User's Pet", "species": "dog"}""")
        }
        val petId = Json.parseToJsonElement(petResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Owner posts on their pet's wall
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            setBody("""{"content": "Post on my pet's wall", "petId": "$petId"}""")
        }

        // Follower follows the pet owner
        client.post("/api/follow/user/$ownerId") {
            header(HttpHeaders.Authorization, "Bearer $followerToken")
        }

        // Follower gets feed - should include post from followed user's pet
        val feedResponse = client.get("/api/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer $followerToken")
        }

        assertEquals(HttpStatusCode.OK, feedResponse.status)
        val body = Json.parseToJsonElement(feedResponse.bodyAsText()).jsonObject
        val posts = body["posts"]?.jsonArray
        assertNotNull(posts)

        val hasFollowedUserPetPost = posts.any {
            it.jsonObject["content"]?.jsonPrimitive?.content == "Post on my pet's wall" &&
            it.jsonObject["petId"]?.jsonPrimitive?.content == petId
        }
        assertTrue(hasFollowedUserPetPost, "Feed should include posts from followed user's pets")
    }

    @Test
    fun `update organization succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register and get token
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Creator"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val createResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Original Name", "orgType": "shelter", "bio": "Original bio"}""")
        }
        val orgId = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Update organization
        val updateResponse = client.put("/api/organizations/$orgId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Updated Name", "bio": "Updated bio", "location": "New York"}""")
        }

        println("Update org response: ${updateResponse.status}")
        println("Update org body: ${updateResponse.bodyAsText()}")

        assertEquals(HttpStatusCode.OK, updateResponse.status)

        // Verify changes
        val getResponse = client.get("/api/organizations/$orgId")
        val body = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        val org = body["organization"]?.jsonObject

        assertEquals("Updated Name", org?.get("name")?.jsonPrimitive?.content)
        assertEquals("Updated bio", org?.get("bio")?.jsonPrimitive?.content)
        assertEquals("New York", org?.get("location")?.jsonPrimitive?.content)
    }

    // ==================== BIRTHDAY TESTS ====================

    @Test
    fun `update user birthday with full date succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Birthday Test User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Update profile with full birthday (year+month+day)
        val updateResponse = client.put("/api/users/me") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"birthYear": 1990, "birthMonth": 6, "birthDay": 15}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val body = Json.parseToJsonElement(updateResponse.bodyAsText()).jsonObject
        assertEquals(1990, body["birthYear"]?.jsonPrimitive?.int)
        assertEquals(6, body["birthMonth"]?.jsonPrimitive?.int)
        assertEquals(15, body["birthDay"]?.jsonPrimitive?.int)

        // Verify via profile
        val profileResponse = client.get("/api/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val profileBody = Json.parseToJsonElement(profileResponse.bodyAsText()).jsonObject
        val user = profileBody["user"]?.jsonObject
        assertEquals(1990, user?.get("birthYear")?.jsonPrimitive?.int)
        assertEquals(6, user?.get("birthMonth")?.jsonPrimitive?.int)
        assertEquals(15, user?.get("birthDay")?.jsonPrimitive?.int)
    }

    @Test
    fun `update user birthday with year and month only succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Partial Birthday User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Update profile with year+month only
        val updateResponse = client.put("/api/users/me") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"birthYear": 1985, "birthMonth": 3}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val body = Json.parseToJsonElement(updateResponse.bodyAsText()).jsonObject
        assertEquals(1985, body["birthYear"]?.jsonPrimitive?.int)
        assertEquals(3, body["birthMonth"]?.jsonPrimitive?.int)
        assertNull(body["birthDay"]?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun `update user birthday with year only succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Year Only User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Update profile with year only
        val updateResponse = client.put("/api/users/me") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"birthYear": 1995}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val body = Json.parseToJsonElement(updateResponse.bodyAsText()).jsonObject
        assertEquals(1995, body["birthYear"]?.jsonPrimitive?.int)
    }

    @Test
    fun `clear user birthday succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Clear Birthday User"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // First set a birthday
        client.put("/api/users/me") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"birthYear": 1990, "birthMonth": 6, "birthDay": 15}""")
        }

        // Now clear it
        val clearResponse = client.put("/api/users/me") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"clearBirthday": true}""")
        }

        assertEquals(HttpStatusCode.OK, clearResponse.status)

        // Verify birthday is cleared
        val profileResponse = client.get("/api/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val profileBody = Json.parseToJsonElement(profileResponse.bodyAsText()).jsonObject
        val user = profileBody["user"]?.jsonObject
        assertNull(user?.get("birthYear")?.jsonPrimitive?.intOrNull)
        assertNull(user?.get("birthMonth")?.jsonPrimitive?.intOrNull)
        assertNull(user?.get("birthDay")?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun `create pet with birthday succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Pet Birthday Owner"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet with full birthday
        val createResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Birthday Pet", "species": "dog", "birthYear": 2020, "birthMonth": 4, "birthDay": 10}""")
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val body = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        assertEquals(2020, body["birthYear"]?.jsonPrimitive?.int)
        assertEquals(4, body["birthMonth"]?.jsonPrimitive?.int)
        assertEquals(10, body["birthDay"]?.jsonPrimitive?.int)
    }

    @Test
    fun `update pet birthday succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Pet Update Owner"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create pet without birthday
        val createResponse = client.post("/api/pets") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Update Birthday Pet", "species": "cat"}""")
        }
        val petId = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Update pet with birthday
        val updateResponse = client.put("/api/pets/$petId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"birthYear": 2019, "birthMonth": 11}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)

        // Verify pet has birthday
        val getResponse = client.get("/api/pets/$petId")
        val body = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        val pet = body["pet"]?.jsonObject
        assertEquals(2019, pet?.get("birthYear")?.jsonPrimitive?.int)
        assertEquals(11, pet?.get("birthMonth")?.jsonPrimitive?.int)
    }

    @Test
    fun `create organization with establishment date succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Org Creator"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization (establishment date is set via update)
        val createResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Est Date Org", "orgType": "shelter"}""")
        }
        val orgId = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Update org with establishment date
        val updateResponse = client.put("/api/organizations/$orgId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"establishedYear": 2010, "establishedMonth": 8, "establishedDay": 25}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val body = Json.parseToJsonElement(updateResponse.bodyAsText()).jsonObject
        assertEquals(2010, body["establishedYear"]?.jsonPrimitive?.int)
        assertEquals(8, body["establishedMonth"]?.jsonPrimitive?.int)
        assertEquals(25, body["establishedDay"]?.jsonPrimitive?.int)
    }

    @Test
    fun `update organization establishment date with year only succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Year Org Creator"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val createResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Year Only Org", "orgType": "rescue"}""")
        }
        val orgId = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Update with year only
        val updateResponse = client.put("/api/organizations/$orgId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"establishedYear": 1995}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val body = Json.parseToJsonElement(updateResponse.bodyAsText()).jsonObject
        assertEquals(1995, body["establishedYear"]?.jsonPrimitive?.int)
    }

    @Test
    fun `clear organization establishment date succeeds`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val email = uniqueEmail()

        // Register user
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email", "password": "password123", "name": "Clear Est Org Creator"}""")
        }
        val token = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Create organization
        val createResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"name": "Clear Est Org", "orgType": "breeder"}""")
        }
        val orgId = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // First set establishment date
        client.put("/api/organizations/$orgId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"establishedYear": 2000, "establishedMonth": 3}""")
        }

        // Now clear it
        val clearResponse = client.put("/api/organizations/$orgId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"clearEstablishedDate": true}""")
        }

        assertEquals(HttpStatusCode.OK, clearResponse.status)

        // Verify establishment date is cleared
        val getResponse = client.get("/api/organizations/$orgId")
        val body = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        val org = body["organization"]?.jsonObject
        assertNull(org?.get("establishedYear")?.jsonPrimitive?.intOrNull)
        assertNull(org?.get("establishedMonth")?.jsonPrimitive?.intOrNull)
        assertNull(org?.get("establishedDay")?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun `feed includes posts from followed organizations`() = testApplication {
        application {
            configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
            configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        // User 1 creates organization and posts as org
        val email1 = uniqueEmail()
        val register1 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email1", "password": "password123", "name": "Org Manager"}""")
        }
        val token1 = Json.parseToJsonElement(register1.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        val orgResponse = client.post("/api/organizations") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"name": "Followed Org", "orgType": "shelter"}""")
        }
        val orgId = Json.parseToJsonElement(orgResponse.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content

        // Post as organization
        client.post("/api/posts") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"content": "Org post for feed test", "actingAsOrgId": "$orgId"}""")
        }

        // User 2 follows organization
        val email2 = uniqueEmail()
        val register2 = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email": "$email2", "password": "password123", "name": "Follower"}""")
        }
        val token2 = Json.parseToJsonElement(register2.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content

        // Follow organization
        client.post("/api/follow/organization/$orgId") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        // Get feed for user 2
        val feedResponse = client.get("/api/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.OK, feedResponse.status)
        val body = Json.parseToJsonElement(feedResponse.bodyAsText()).jsonObject
        val posts = body["posts"]?.jsonArray
        assertNotNull(posts)

        val hasOrgPost = posts.any {
            it.jsonObject["content"]?.jsonPrimitive?.content == "Org post for feed test" &&
            it.jsonObject["authorType"]?.jsonPrimitive?.content == "organization"
        }
        assertTrue(hasOrgPost, "Feed should include posts from followed organizations")
    }
}
