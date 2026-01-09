package com.petbook

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.petbook.auth.AuthService
import com.petbook.auth.authRoutes
import com.petbook.db.TypeDBConfig
import com.petbook.db.TypeDBService
import com.petbook.follow.FollowRepository
import com.petbook.follow.followRoutes
import com.petbook.organization.OrganizationRepository
import com.petbook.organization.organizationRoutes
import com.petbook.pet.PetRepository
import com.petbook.pet.petRoutes
import com.petbook.post.PostRepository
import com.petbook.post.postRoutes
import com.petbook.user.UserRepository
import com.petbook.user.userRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Application")

fun main() {
    val config = TypeDBConfig.fromEnvironment()
    val typedbService = TypeDBService(config)

    // Connect to TypeDB
    if (typedbService.connect()) {
        logger.info("Connected to TypeDB successfully")
    } else {
        logger.warn("Failed to connect to TypeDB - running without database")
    }

    // JWT configuration
    val jwtSecret = System.getenv("JWT_SECRET") ?: "petbook-secret-key-change-in-production"
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "petbook"
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "petbook-users"

    // Create repositories and services
    val userRepository = UserRepository(typedbService)
    val petRepository = PetRepository(typedbService)
    val postRepository = PostRepository(typedbService)
    val followRepository = FollowRepository(typedbService)
    val organizationRepository = OrganizationRepository(typedbService)
    val authService = AuthService(userRepository, jwtSecret, jwtIssuer, jwtAudience)

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configurePlugins(jwtSecret, jwtIssuer, jwtAudience)
        configureRouting(authService, userRepository, petRepository, postRepository, followRepository, organizationRepository)
    }.start(wait = true)
}

fun Application.configurePlugins(jwtSecret: String, jwtIssuer: String, jwtAudience: String) {
    // JSON serialization
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true  // Include fields with default values in JSON output
        })
    }

    // CORS for frontend
    install(CORS) {
        allowHost("localhost:4200")
        allowHost("127.0.0.1:4200")
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyMethod()
        allowCredentials = true
    }

    // JWT Authentication
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "petbook"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is invalid or expired"))
            }
        }
    }

    // Error handling
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }
}

fun Application.configureRouting(
    authService: AuthService,
    userRepository: UserRepository,
    petRepository: PetRepository,
    postRepository: PostRepository,
    followRepository: FollowRepository,
    organizationRepository: OrganizationRepository
) {
    routing {
        // Health check
        get("/api/health") {
            call.respond(mapOf("status" to "ok"))
        }

        // Auth routes (public)
        authRoutes(authService)

        // User routes
        userRoutes(userRepository, petRepository)

        // Pet routes
        petRoutes(petRepository, organizationRepository)

        // Post routes
        postRoutes(postRepository, organizationRepository)

        // Follow routes
        followRoutes(followRepository)

        // Organization routes
        organizationRoutes(organizationRepository, petRepository)
    }
}
