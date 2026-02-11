package com.popcorn.coupon.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}")
    private val jwtSecret: String,

    @Value("\${jwt.expiration}")
    private val jwtExpirationMs: Long
) {

    private val logger = KotlinLogging.logger {}
    private val key: SecretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))

    fun generateToken(userId: Long, email: String, role: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("email", email)
            .claim("role", role)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun getUserIdFromToken(token: String): Long {
        val claims = parseToken(token)
        return claims.subject.toLong()
    }

    fun getEmailFromToken(token: String): String {
        val claims = parseToken(token)
        return claims["email"] as String
    }

    fun getRoleFromToken(token: String): String {
        val claims = parseToken(token)
        return claims["role"] as String
    }

    fun getAuthenticationFromToken(token: String): Authentication {
        val claims = parseToken(token)
        val userId = claims.subject
        val role = claims["role"] as String
        val email = claims["email"] as String

        val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
        val principal = UserPrincipal(userId.toLong(), email, role)

        return UsernamePasswordAuthenticationToken(principal, token, authorities)
    }

    fun validateToken(token: String): Boolean {
        return try {
            parseToken(token)
            true
        } catch (e: Exception) {
            logger.error(e) { "JWT token validation failed" }
            false
        }
    }

    private fun parseToken(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
    }
}