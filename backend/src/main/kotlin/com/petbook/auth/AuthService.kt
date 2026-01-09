package com.petbook.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.petbook.user.User
import com.petbook.user.UserRepository
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

class AuthService(
    private val userRepository: UserRepository,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String
) {
    private val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    // Token expiry: 7 days
    private val tokenExpiryMs = 7 * 24 * 60 * 60 * 1000L

    fun register(name: String, email: String, password: String): Result<AuthResponse> {
        // Check if email already exists
        if (userRepository.emailExists(email)) {
            return Result.failure(Exception("Email already registered"))
        }

        // Hash password
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

        // Create user
        val user = userRepository.createUser(email, passwordHash, name)
            ?: return Result.failure(Exception("Failed to create user"))

        // Generate token
        val token = generateToken(user)

        return Result.success(AuthResponse(
            token = token,
            user = UserInfo(user.id, user.name, user.email)
        ))
    }

    fun login(email: String, password: String): Result<AuthResponse> {
        // Find user
        val user = userRepository.findByEmail(email)
            ?: return Result.failure(Exception("Invalid email or password"))

        // Verify password
        if (!BCrypt.checkpw(password, user.passwordHash)) {
            return Result.failure(Exception("Invalid email or password"))
        }

        // Generate token
        val token = generateToken(user)

        return Result.success(AuthResponse(
            token = token,
            user = UserInfo(user.id, user.name, user.email)
        ))
    }

    fun validateToken(token: String): String? {
        return try {
            val verifier = JWT.require(jwtAlgorithm)
                .withIssuer(jwtIssuer)
                .withAudience(jwtAudience)
                .build()
            val decoded = verifier.verify(token)
            decoded.subject
        } catch (e: Exception) {
            null
        }
    }

    fun getUserById(userId: String): User? {
        return userRepository.findById(userId)
    }

    private fun generateToken(user: User): String {
        return JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withSubject(user.id)
            .withClaim("email", user.email)
            .withClaim("name", user.name)
            .withExpiresAt(Date(System.currentTimeMillis() + tokenExpiryMs))
            .sign(jwtAlgorithm)
    }
}
