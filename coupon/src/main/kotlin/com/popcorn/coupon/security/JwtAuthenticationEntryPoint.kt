package com.popcorn.coupon.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {

    private val logger = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper()

    @Throws(IOException::class)
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        logger.error { "Unauthorized error: ${authException.message}" }

        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.status = HttpServletResponse.SC_UNAUTHORIZED

        val errorResponse = mapOf(
            "error" to "Unauthorized",
            "message" to "인증이 필요한 서비스입니다",
            "status" to HttpServletResponse.SC_UNAUTHORIZED,
            "path" to request.requestURI
        )

        objectMapper.writeValue(response.outputStream, errorResponse)
    }
}