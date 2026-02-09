package com.example.orderquery.global.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.example.orderquery.domain.summary.dto.OrderSummaryDto;
import com.example.orderquery.domain.summary.entity.OrderSummary;
import com.example.orderquery.domain.summary.exception.SummaryException;
import com.example.orderquery.domain.summary.repository.OrderSummaryRepository;
import com.example.orderquery.global.exception.OwnerAuthException;
import com.popcorn.common.dto.CommonResponseCode;
import com.popcorn.common.filter.PassportPrincipal;
import com.popcorn.common.security.UserRole;

@Service
public class OwnerAuthService {

    private final OrderSummaryRepository orderSummaryRepository;

    public OwnerAuthService(OrderSummaryRepository orderSummaryRepository) {
        this.orderSummaryRepository = orderSummaryRepository;
    }

    public record OwnerContext(Long ownerId, UserRole role) {}

    public OwnerContext resolveOwner(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw OwnerAuthException.unauthenticated();
        }

        PassportPrincipal passport = extractPassport(authentication.getPrincipal());
        Long userId = extractUserId(authentication, passport);
        if (userId == null) {
            throw OwnerAuthException.userIdRequired();
        }

        String roleValue = resolveRoleValue(authentication, passport);
        if (roleValue == null || roleValue.isBlank()) {
            throw OwnerAuthException.invalidRole();
        }
        roleValue = normalizeRoleValue(roleValue);

        UserRole role;
        try {
            role = UserRole.valueOf(roleValue);
        } catch (IllegalArgumentException ex) {
            throw OwnerAuthException.invalidRole();
        }

        if (role != UserRole.OWNER && role != UserRole.MANAGER) {
            throw OwnerAuthException.notOwner();
        }

        return new OwnerContext(role == UserRole.OWNER ? userId : null, role);
    }

    public void authorizePopupAccess(OrderSummaryDto summaryDto, OwnerContext context) {
        if (context.role() != UserRole.OWNER) {
            return;
        }
        Long summaryOwner = summaryDto.getOwnerId();
        if (summaryOwner == null || !summaryOwner.equals(context.ownerId())) {
            throw OwnerAuthException.notPopupOwner();
        }
    }

    public Long getCurrentOwnerId(Authentication authentication) {
        OwnerContext context = resolveOwner(authentication);
        if (context.role() != UserRole.OWNER) {
            throw OwnerAuthException.notOwner();
        }
        return context.ownerId();
    }

    public void requireOwnedPopup(UUID storeId, UUID popupId, Long ownerId) {
        if (ownerId == null) {
            return;
        }
        OrderSummary summary = orderSummaryRepository.findByStoreIdAndPopupId(storeId, popupId)
                .orElseThrow(() -> SummaryException.notFound(storeId, popupId));
        Long summaryOwner = summary.getOwnerId();
        if (summaryOwner == null || !summaryOwner.equals(ownerId)) {
            throw OwnerAuthException.notPopupOwner();
        }
    }

    private Long extractUserId(Authentication authentication, PassportPrincipal passport) {
        if (passport != null && passport.userId() != null) {
            return passport.userId();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }

        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            try {
                return Long.parseLong(name);
            } catch (NumberFormatException ex) {
                throw OwnerAuthException.invalidPrincipal();
            }
        }
        return null;
    }

    private PassportPrincipal extractPassport(Object principal) {
        if (principal instanceof PassportPrincipal passport) {
            return passport;
        }
        return null;
    }

    private String resolveRoleValue(Authentication authentication, PassportPrincipal passport) {
        if (passport != null && passport.role() != null && !passport.role().isBlank()) {
            return passport.role();
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth != null && !auth.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String normalizeRoleValue(String roleValue) {
        if (roleValue.startsWith("ROLE_")) {
            return roleValue.substring(5);
        }
        return roleValue;
    }
}
