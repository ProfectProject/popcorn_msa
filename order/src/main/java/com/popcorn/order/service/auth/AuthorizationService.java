package com.popcorn.order.service;

import com.popcorn.common.security.PassportPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 사용자 권한 검증 서비스
 *
 * [역할]
 * - JWT 토큰에서 사용자 정보 추출
 * - MANAGER/OWNER 역할 검증
 * - 리소스별 접근 권한 검증
 */
@Service
@Slf4j
public class AuthorizationService {

    /**
     * 사용자 권한 검증
     *
     * @param authentication Spring Security Authentication 객체
     * @param requiredRoles 필요한 역할들
     * @param resourceType 리소스 타입 (POPUP, ORDER, STORE)
     * @param resourceId 리소스 ID
     * @return 권한 검증 결과
     */
    public boolean hasPermission(Authentication authentication, String[] requiredRoles,
                               String resourceType, UUID resourceId) {
        try {
            // 1. 인증 정보 검증
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("인증되지 않은 사용자의 접근 시도");
                return false;
            }

            // 2. JWT에서 사용자 정보 추출
            UserInfo userInfo = extractUserInfo(authentication);
            if (userInfo == null) {
                log.warn("JWT에서 사용자 정보 추출 실패");
                return false;
            }

            // 3. 역할 검증
            if (!hasRequiredRole(userInfo.getRole(), requiredRoles)) {
                log.warn("사용자 역할이 요구사항과 맞지 않음 - userId: {}, userRole: {}, requiredRoles: {}",
                        userInfo.getUserId(), userInfo.getRole(), List.of(requiredRoles));
                return false;
            }

            // 4. 리소스별 접근 권한 검증
            if (resourceType != null && !resourceType.isEmpty() && resourceId != null) {
                if (!hasResourceAccess(userInfo, resourceType, resourceId)) {
                    log.warn("리소스 접근 권한 없음 - userId: {}, resourceType: {}, resourceId: {}",
                            userInfo.getUserId(), resourceType, resourceId);
                    return false;
                }
            }

            log.info("권한 검증 성공 - userId: {}, role: {}, resourceType: {}, resourceId: {}",
                    userInfo.getUserId(), userInfo.getRole(), resourceType, resourceId);
            return true;

        } catch (Exception e) {
            log.error("권한 검증 중 오류 발생: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Authentication 객체에서 사용자 정보 추출
     */
    private UserInfo extractUserInfo(Authentication authentication) {
        try {
            // Gateway에서 처리된 PassportPrincipal 사용
            Object principal = authentication.getPrincipal();
            if (principal instanceof PassportPrincipal) {
                PassportPrincipal passportPrincipal = (PassportPrincipal) principal;
                return UserInfo.builder()
                        .userId(passportPrincipal.getUserId())
                        .role(passportPrincipal.getRole())
                        .storeId(null) // 필요 시 PassportPrincipal에 storeId 추가 가능
                        .authorizedPopupIds(List.of()) // 필요 시 PassportPrincipal에 추가 가능
                        .build();
            }

            // Fallback: Authentication에서 기본 정보 추출
            String principalName = authentication.getName();
            Long userId = principalName != null ? Long.parseLong(principalName) : null;
            String role = extractRoleFromAuthorities(authentication.getAuthorities());

            return UserInfo.builder()
                    .userId(userId)
                    .role(role)
                    .storeId(null)
                    .authorizedPopupIds(List.of())
                    .build();

        } catch (Exception e) {
            log.error("사용자 정보 추출 실패: {}", e.getMessage(), e);
        }
        return null;
    }


    /**
     * GrantedAuthority에서 역할 추출 (JWT 실패 시 fallback)
     */
    private String extractRoleFromAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5)) // "ROLE_" 제거
                .findFirst()
                .orElse("USER");
    }

    /**
     * 필요한 역할 보유 여부 검증
     */
    private boolean hasRequiredRole(String userRole, String[] requiredRoles) {
        if (userRole == null || requiredRoles == null || requiredRoles.length == 0) {
            return false;
        }

        for (String requiredRole : requiredRoles) {
            if (requiredRole.equals(userRole)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 리소스 접근 권한 검증
     */
    private boolean hasResourceAccess(UserInfo userInfo, String resourceType, UUID resourceId) {
        switch (resourceType.toUpperCase()) {
            case "POPUP":
                return hasPopupAccess(userInfo, resourceId);
            case "ORDER":
                return hasOrderAccess(userInfo, resourceId);
            case "STORE":
                return hasStoreAccess(userInfo, resourceId);
            default:
                log.warn("알 수 없는 리소스 타입: {}", resourceType);
                return false;
        }
    }

    /**
     * 팝업 접근 권한 검증
     */
    private boolean hasPopupAccess(UserInfo userInfo, UUID popupId) {
        // OWNER는 자신의 스토어 내 모든 팝업에 접근 가능
        if ("OWNER".equals(userInfo.getRole())) {
            // TODO: 실제로는 팝업이 해당 스토어에 속하는지 확인 필요
            // 이벤트 기반으로 popup의 storeId를 조회하고 userInfo.storeId와 비교
            log.debug("OWNER 권한으로 팝업 접근 허용 - popupId: {}", popupId);
            return true;
        }

        // MANAGER는 권한이 부여된 팝업에만 접근 가능
        if ("MANAGER".equals(userInfo.getRole())) {
            if (userInfo.getAuthorizedPopupIds() != null) {
                boolean hasAccess = userInfo.getAuthorizedPopupIds().contains(popupId.toString());
                log.debug("MANAGER 팝업 접근 권한 검증 - popupId: {}, hasAccess: {}", popupId, hasAccess);
                return hasAccess;
            }
        }

        return false;
    }

    /**
     * 주문 접근 권한 검증
     */
    private boolean hasOrderAccess(UserInfo userInfo, UUID orderId) {
        // TODO: 실제로는 주문이 속한 팝업을 조회하고, 그 팝업에 대한 권한을 확인해야 함
        // OrderQueryService를 통해 주문의 popupId를 조회 후 hasPopupAccess() 호출

        log.debug("주문 접근 권한 임시 허용 - orderId: {}, userId: {}", orderId, userInfo.getUserId());
        return true; // 임시로 모든 접근 허용
    }

    /**
     * 스토어 접근 권한 검증
     */
    private boolean hasStoreAccess(UserInfo userInfo, UUID storeId) {
        // OWNER는 자신의 스토어에만 접근 가능
        if ("OWNER".equals(userInfo.getRole()) && userInfo.getStoreId() != null) {
            boolean hasAccess = userInfo.getStoreId().equals(storeId.toString()) ||
                               userInfo.getStoreId().toString().equals(storeId.toString());
            log.debug("스토어 접근 권한 검증 - storeId: {}, userStoreId: {}, hasAccess: {}",
                     storeId, userInfo.getStoreId(), hasAccess);
            return hasAccess;
        }

        // MANAGER는 스토어 직접 접근 불가 (팝업 단위로만 접근)
        return false;
    }

    /**
     * 사용자 정보 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class UserInfo {
        private Long userId;
        private String role;
        private Long storeId;
        private List<String> authorizedPopupIds;
    }
}
