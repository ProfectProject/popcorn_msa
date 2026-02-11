package com.popcorn.coupon.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
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
    private val key: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))

    fun generateToken(userId: Long, email: String, role: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        return Jwts.builder()
            .setSubject(userId.toString())
            .claim("id", userId)
            .claim("email", email)
            .claim("role", role)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun getUserIdFromToken(token: String): Long {
        val claims = parseToken(token)
        return getUserIdFromClaims(claims)
    }

    fun getEmailFromToken(token: String): String {
        val claims = parseToken(token)
        return getRequiredStringClaim(claims, "email")
    }

    fun getRoleFromToken(token: String): String {
        val claims = parseToken(token)
        return getRequiredStringClaim(claims, "role")
    }

    fun getAuthenticationFromToken(token: String): Authentication {
        val claims = parseToken(token)
        val userId = getUserIdFromClaims(claims)
        val role = getRequiredStringClaim(claims, "role")
        val email = getRequiredStringClaim(claims, "email")

        val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
        val principal = UserPrincipal(userId, email, role)

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

    private fun getRequiredStringClaim(claims: Claims, key: String): String {
        return claims[key]?.toString()
            ?: throw IllegalArgumentException("JWT token does not contain required '$key' claim")
    }

    private fun getUserIdFromClaims(claims: Claims): Long {
        val idClaim = claims["id"]
        return when (idClaim) {
            is Number -> idClaim.toLong()
            is String -> idClaim.toLongOrNull()
            else -> null
        } ?: claims.subject?.toLongOrNull()
        ?: throw IllegalArgumentException("JWT token does not contain user id")
    }
}
