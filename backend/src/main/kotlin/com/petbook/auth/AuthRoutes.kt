package com.petbook.auth

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {

    route("/api/auth") {

        post("/register") {
            val request = call.receive<RegisterRequest>()

            // Validate input
            if (request.email.isBlank() || request.password.isBlank() || request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("All fields are required"))
                return@post
            }

            if (request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be at least 6 characters"))
                return@post
            }

            if (!request.email.contains("@")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid email format"))
                return@post
            }

            authService.register(request.name, request.email, request.password)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.Created, response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(error.message ?: "Registration failed"))
                }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()

            // Validate input
            if (request.email.isBlank() || request.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Email and password are required"))
                return@post
            }

            authService.login(request.email, request.password)
                .onSuccess { response ->
                    call.respond(HttpStatusCode.OK, response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(error.message ?: "Login failed"))
                }
        }

        get("/me") {
            // This endpoint will be protected by JWT auth
            val userId = call.request.headers["X-User-Id"]
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not authenticated"))
                return@get
            }

            val user = authService.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, UserInfo(user.id, user.name, user.email))
        }
    }
}
