package com.popcorn.users.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import com.popcorn.users.auth.dto.CustomUserDetails;
import com.popcorn.users.auth.dto.LoginRequest;
import com.popcorn.users.auth.dto.LoginResponse;
import com.popcorn.users.auth.jwt.JwtUtil;
import com.popcorn.users.auth.dto.RefreshTokenRequest;
import com.popcorn.users.auth.dto.RefreshTokenResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiration:3600000}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-expiration-ms:1209600000}")
    private long refreshTokenExpirationMs;

    public LoginResponse login(LoginRequest request) {

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                );

        Authentication authentication = authenticationManager.authenticate(authToken);

        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();

        Long userId = customUserDetails.getUserId();
        String role = customUserDetails.getAuthorities()
                .iterator().next()
                .getAuthority()
                .replace("ROLE_", "");

        String accesstoken = jwtUtil.createJwt(userId,customUserDetails.getUsername(), role, accessTokenExpirationMs);
        String refreshtoken = jwtUtil.createRefreshJwt(userId,customUserDetails.getUsername(), role, refreshTokenExpirationMs);

        try {
            refreshTokenService.saveRefreshToken(userId, refreshtoken, refreshTokenExpirationMs);
        } catch (Exception e) {
            // Redis 이슈로 로그인 자체가 실패하지 않도록 fail-open 처리
            log.warn("Failed to save refresh token for userId={} (login continues): {}", userId, e.getMessage());
        }
        return new LoginResponse(accesstoken,refreshtoken);
    }

    public RefreshTokenResponse refreshAccessToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("Refresh token is required.");
        }

        if (jwtUtil.isExpired(refreshToken)) {
            throw new RuntimeException("Refresh token expired.");
        }

        //String tokenType = jwtUtil.getTokenType(refreshToken);
        //if (!"refresh".equals(tokenType)) {
        //    throw new RuntimeException("Invalid refresh token.");
        //}
        Long userId = jwtUtil.getUserId(refreshToken);
        String email = jwtUtil.getUsername(refreshToken);
        String role = jwtUtil.getRole(refreshToken);

        if (!refreshTokenService.isRefreshTokenValid(userId, refreshToken)) {
            throw new RuntimeException("Refresh token not recognized.");
        }
        String accessToken = jwtUtil.createJwt(userId, email, role, accessTokenExpirationMs);
        return new RefreshTokenResponse(accessToken, "Bearer", accessTokenExpirationMs);

    }

    // 임시 테스트용 메서드 - 비밀번호 해시 생성
    public String generatePasswordHash(String password) {
        return passwordEncoder.encode(password);
    }
}
