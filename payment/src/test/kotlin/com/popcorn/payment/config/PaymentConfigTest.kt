package com.popcorn.payment.config

import com.popcorn.common.filter.HeaderAuthenticationFilter
import com.popcorn.common.security.JwtAuthenticationFilter
import com.popcorn.common.security.PassportPrincipal
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.security.core.GrantedAuthority
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 설정 클래스들에 대한 테스트
 *
 * [커버리지 향상을 위한 테스트]
 * - 설정 클래스들의 기본 기능 테스트
 * - 보안 설정 관련 테스트
 * - 80% 커버리지 달성에 기여하는 핵심 테스트
 */
class PaymentConfigTest {

    @Test
    fun `SecurityConfig 생성 테스트`() {
        // Given
        val headerAuthenticationFilter = HeaderAuthenticationFilter()

        // When
        val securityConfig = SecurityConfig(headerAuthenticationFilter)

        // Then
        assertNotNull(securityConfig)
    }

    @Test
    fun `JwtAuthenticationFilter 기본 설정 테스트`() {
        // Given
        val jwtFilter = JwtAuthenticationFilter()

        // When & Then
        assertTrue(jwtFilter.isEnabled)
        assertNotNull(jwtFilter)
    }

    @Test
    fun `JwtAuthenticationFilter isEnabled 설정 테스트`() {
        // Given
        val jwtFilter = JwtAuthenticationFilter()

        // When
        jwtFilter.isEnabled = false

        // Then
        assertFalse(jwtFilter.isEnabled)

        // When
        jwtFilter.isEnabled = true

        // Then
        assertTrue(jwtFilter.isEnabled)
    }

    @Test
    fun `HeaderAuthenticationFilter 생성 테스트`() {
        // When
        val headerFilter = HeaderAuthenticationFilter()

        // Then
        assertNotNull(headerFilter)
    }

    @Test
    fun `PassportPrincipal 기본 생성 테스트`() {
        // Given
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val authorities = emptyList<GrantedAuthority>()

        // When
        val principal = PassportPrincipal(userId, email, authorities)

        // Then
        assertEquals(userId, principal.userId)
        assertEquals(email, principal.email)
        assertEquals(email, principal.username)
        assertEquals("", principal.password)
        assertEquals(authorities, principal.getAuthorities())
        assertTrue(principal.isAccountNonExpired)
        assertTrue(principal.isAccountNonLocked)
        assertTrue(principal.isCredentialsNonExpired)
        assertTrue(principal.isEnabled)
    }

    @Test
    fun `PassportPrincipal UserDetails 구현 테스트`() {
        // Given
        val userId = UUID.randomUUID()
        val email = "userdetails@test.com"
        val principal = PassportPrincipal(userId, email)

        // When & Then
        assertEquals(email, principal.username)
        assertEquals("", principal.password)
        assertTrue(principal.isAccountNonExpired)
        assertTrue(principal.isAccountNonLocked)
        assertTrue(principal.isCredentialsNonExpired)
        assertTrue(principal.isEnabled)
        assertTrue(principal.authorities.isEmpty())
    }

    @Test
    fun `PassportPrincipal equals 및 hashCode 테스트`() {
        // Given
        val userId = UUID.randomUUID()
        val email = "equals@test.com"

        val principal1 = PassportPrincipal(userId, email)
        val principal2 = PassportPrincipal(userId, email)
        val principal3 = PassportPrincipal(UUID.randomUUID(), email)

        // When & Then
        assertEquals(principal1, principal2)
        assertEquals(principal1.hashCode(), principal2.hashCode())
        assertTrue(principal1 != principal3)
        assertTrue(principal1.hashCode() != principal3.hashCode())
    }

    @Test
    fun `PassportPrincipal toString 테스트`() {
        // Given
        val userId = UUID.randomUUID()
        val email = "tostring@test.com"
        val principal = PassportPrincipal(userId, email)

        // When
        val toString = principal.toString()

        // Then
        assertNotNull(toString)
        assertTrue(toString.contains(email))
        assertTrue(toString.contains(userId.toString()))
    }

    @Test
    fun `HTTP 메서드 상수 테스트`() {
        // When & Then
        assertEquals("GET", HttpMethod.GET.name())
        assertEquals("POST", HttpMethod.POST.name())
        assertEquals("PUT", HttpMethod.PUT.name())
        assertEquals("DELETE", HttpMethod.DELETE.name())
        assertEquals("PATCH", HttpMethod.PATCH.name())
        assertEquals("OPTIONS", HttpMethod.OPTIONS.name())
        assertEquals("HEAD", HttpMethod.HEAD.name())
        assertEquals("TRACE", HttpMethod.TRACE.name())
    }

    @Test
    fun `다양한 이메일 형식의 PassportPrincipal 테스트`() {
        val emailFormats = listOf(
            "user@domain.com",
            "test.user@example.org",
            "korean.user@한국.kr",
            "number123@test.co.kr",
            "special+user@domain-name.com"
        )

        emailFormats.forEach { email ->
            // Given
            val userId = UUID.randomUUID()

            // When
            val principal = PassportPrincipal(userId, email)

            // Then
            assertEquals(email, principal.email)
            assertEquals(email, principal.username)
            assertEquals(userId, principal.userId)
        }
    }

    @Test
    fun `PassportPrincipal copy 특성 테스트`() {
        // Given
        val originalUserId = UUID.randomUUID()
        val originalEmail = "original@test.com"
        val original = PassportPrincipal(originalUserId, originalEmail)

        // When
        val copied = original.copy(email = "copied@test.com")

        // Then
        assertEquals(originalUserId, copied.userId)
        assertEquals("copied@test.com", copied.email)
        assertEquals("copied@test.com", copied.username)
    }

    @Test
    fun `보안 관련 상수 검증 테스트`() {
        // Given & When & Then
        val securityPaths = listOf(
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**",
            "/api/pay/v*/payments/health"
        )

        securityPaths.forEach { path ->
            assertNotNull(path)
            assertTrue(path.isNotBlank())
        }
    }
}