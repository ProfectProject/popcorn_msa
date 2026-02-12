package com.popcorn.users.users.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.popcorn.users.users.dto.SignupRequest;
import com.popcorn.users.users.dto.SignupResponse;
import com.popcorn.users.users.dto.UserAddressRequest;
import com.popcorn.users.users.dto.UserUpdateRequest;
import com.popcorn.users.users.entity.User;
import com.popcorn.users.users.entity.UserAddress;
//import com.popcorn.users.users.event.TestProducer;
import com.popcorn.users.users.repository.UserAddressRepository;
import com.popcorn.users.users.repository.UserRepository;
//import com.popcorn.demo.global.exception.ValidationException;

//추가
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAddressRepository userAddressRepository;
    //private final TestProducer testProducer;

    //추가



    //추가



    public SignupResponse register(SignupRequest request){
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        if (!request.getPassword().equals(request.getPasswordCheck())) {
            //throw new ValidationException("비밀번호가 일치하지 않습니다.");
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        String encodigPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(encodigPassword);
        user.setPhone(request.getPhone());
        user.setName(request.getName());
        user.setRole(request.getRole());


        User savedUser = userRepository.save(user);

        return SignupResponse.builder()
                .email(savedUser.getEmail())
                .name(savedUser.getName())
                .role(savedUser.getRole())
                .build();

    }

    /**
     * 사용자 ID로 사용자 정보 조회
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + userId));
    }

    /**
     * 사용자 정보 업데이트
     */
    @Transactional
    public User updateUser(Long userId, UserUpdateRequest request) {
        User user = getUserById(userId);

        // 전화번호 전처리 (-, 공백 등 제거 후 숫자만 남김)
        String cleanedPhone = null;
        if (request.getPhone() != null) {
            cleanedPhone = request.getPhone().replaceAll("[^0-9]", "");
            // 전화번호 중복 체크 (현재 사용자 제외)
            userRepository.findByPhone(cleanedPhone).ifPresent(existingUser -> {
                if (!existingUser.getUserId().equals(userId)) {
                    throw new RuntimeException("Phone number already exists");
                }
            });
        }

        // 업데이트할 필드만 변경
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            user.setName(request.getName());
        }
        if (cleanedPhone != null) {
            user.setPhone(cleanedPhone);
        }

        // 비밀번호 변경
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            user.setPassword(encodedPassword);
        }

        return userRepository.save(user);
    }

    /**
     * 사용자 계정 탈퇴
     */
    @Transactional
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        // 기존 이메일 가져오기
        String originalEmail = user.getEmail();

        // 개인정보 마스킹 : 이메일로
        user.setEmail("deleted_" + userId + "_" + originalEmail);
        user.setName("탈퇴회원"+"("+user.getName()+")");

        user.setActive(false); // 비활성화

        userRepository.save(user);
    }

    /**
     *  배송지 정보
     */

    // 주소 목록 조회
    public List<UserAddress> getUserAddresses(Long userId) {
        // 사용자 접근 권한 검증
        validateUserAccess(userId);

        return userAddressRepository.findByUserUserId(userId);
    }

    // 주소 생성
    @Transactional
    public UserAddress createUserAddress(Long userId, UserAddressRequest request) {
        log.debug("createUserAddress called with userId: {}", userId);

        // 사용자 접근 권한 검증
        validateUserAccess(userId);

        User user = getUserById(userId);
        log.debug("User found for userId: {}", userId);
        
        // 기본 주소로 설정하는 경우, 기존 기본 주소들을 false로 변경
        if (request.getIsDefault() != null && request.getIsDefault()) {
            List<UserAddress> existingAddresses = userAddressRepository.findByUserUserId(userId);
            existingAddresses.forEach(addr -> addr.setIsDefault(false));
            userAddressRepository.saveAll(existingAddresses);
        }
        
        UserAddress address = new UserAddress();
        address.setUser(user);
        address.setAddrName(request.getAddrName());
        address.setAddress1(request.getAddress1());
        address.setAddress2(request.getAddress2());
        address.setPostalCode(request.getPostalCode());
        address.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        
        log.debug("Saving address for userId: {}", userId);
        UserAddress savedAddress = userAddressRepository.save(address);
        //testProducer.sendMessage("kafka teset");
        log.debug("Address saved successfully with id: {}", savedAddress.getId());
        return savedAddress;
    }

    /**
     * 사용자 주소 수정
     */
    @Transactional
    public UserAddress updateUserAddress(Long userId, UUID addressId, UserAddressRequest request) {
        // 사용자 접근 권한 검증
        validateUserAccess(userId);

        User user = getUserById(userId);
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("주소를 찾을 수 없습니다: " + addressId));
        
        // 주소가 해당 사용자의 것인지 확인
        if (!address.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("해당 사용자의 주소가 아닙니다.");
        }
        
        // 기본 주소로 설정하는 경우, 기존 기본 주소들을 false로 변경
        if (request.getIsDefault() != null && request.getIsDefault()) {
            List<UserAddress> existingAddresses = userAddressRepository.findByUserUserId(userId);
            existingAddresses.stream()
                    .filter(addr -> !addr.getId().equals(addressId))
                    .forEach(addr -> addr.setIsDefault(false));
            userAddressRepository.saveAll(existingAddresses);
        }
        
        // 업데이트할 필드만 변경
        if (request.getAddrName() != null) {
            address.setAddrName(request.getAddrName());
        }
        if (request.getAddress1() != null) {
            address.setAddress1(request.getAddress1());
        }
        if (request.getAddress2() != null) {
            address.setAddress2(request.getAddress2());
        }
        if (request.getPostalCode() != null) {
            address.setPostalCode(request.getPostalCode());
        }
        if (request.getIsDefault() != null) {
            address.setIsDefault(request.getIsDefault());
        }
        
        return userAddressRepository.save(address);
    }

    /**
     * 사용자 주소 삭제
     */
    @Transactional
    public void deleteUserAddress(Long userId, UUID addressId) {
        // 사용자 접근 권한 검증
        validateUserAccess(userId);

        User user = getUserById(userId);
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("주소를 찾을 수 없습니다: " + addressId));
        
        // 주소가 해당 사용자의 것인지 확인
        if (!address.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("해당 사용자의 주소가 아닙니다.");
        }
        
        userAddressRepository.delete(address);
    }

    /**
     * 기본 배송지 설정
     */
    @Transactional
    public UserAddress setDefaultAddress(Long userId, UUID addressId) {
        try {
            log.debug("setDefaultAddress called with userId: {}, addressId: {}", userId, addressId);

            // 사용자 접근 권한 검증
            validateUserAccess(userId);

            // 사용자 존재 확인
            User user = getUserById(userId);
            log.debug("User found with ID: {}", user.getUserId());

            // 주소 존재 확인
            UserAddress address = userAddressRepository.findById(addressId)
                    .orElseThrow(() -> new RuntimeException("주소를 찾을 수 없습니다: " + addressId));
            log.debug("Address found with ID: {}", address.getId());
            
            // 주소가 해당 사용자의 것인지 확인
            if (!address.getUser().getUserId().equals(userId)) {
                throw new RuntimeException("해당 사용자의 주소가 아닙니다.");
            }
            
            // 기존 기본 주소들을 모두 false로 변경
            List<UserAddress> existingAddresses = userAddressRepository.findByUserUserId(userId);
            log.debug("Found {} existing addresses for userId: {}", existingAddresses.size(), userId);

            for (UserAddress existingAddr : existingAddresses) {
                existingAddr.setIsDefault(false);
            }
            userAddressRepository.saveAll(existingAddresses);

            // 선택한 주소를 기본 주소로 설정
            address.setIsDefault(true);
            log.debug("Setting address {} as default for userId: {}", addressId, userId);
            
            UserAddress savedAddress = userAddressRepository.save(address);
            log.debug("Default address set successfully with ID: {} for userId: {}", savedAddress.getId(), userId);
            
            return savedAddress;
        } catch (Exception e) {
            System.err.println("Error in setDefaultAddress: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 현재 인증된 사용자 ID 가져오기
     */
    private Long getCurrentAuthenticatedUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new RuntimeException("인증되지 않은 사용자입니다.");
            }

            // PassportPrincipal에서 직접 사용자 ID 추출
            Object principal = authentication.getPrincipal();
            if (principal instanceof com.popcorn.common.filter.PassportPrincipal passportPrincipal) {
                return passportPrincipal.userId();
            }

            // Fallback: 문자열로 ID가 전달된 경우
            String userIdStr = authentication.getName();

            // anonymousUser 케이스 처리
            if ("anonymousUser".equals(userIdStr)) {
                log.warn("익명 사용자로 사용자 ID 조회 시도됨");
                throw new RuntimeException("익명 사용자는 사용자 정보에 접근할 수 없습니다.");
            }

            try {
                return Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                log.error("사용자 ID 형식 오류: userIdStr={}", userIdStr);
                throw new RuntimeException("잘못된 사용자 ID 형식입니다.");
            }

        } catch (Exception e) {
            log.error("현재 사용자 ID 조회 실패: {}", e.getMessage());
            throw new RuntimeException("사용자 인증 정보를 확인할 수 없습니다.");
        }
    }

    /**
     * 사용자 접근 권한 검증
     * 현재 인증된 사용자가 요청한 사용자와 동일한지 확인
     */
    private void validateUserAccess(Long requestedUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            log.warn("인증 정보가 없음 - requestedUserId: {}", requestedUserId);
            throw new RuntimeException("인증되지 않은 사용자입니다.");
        }

        // 내부 서비스 호출인 경우 권한 검증 우회 (조기 리턴)
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SYSTEM"))) {
            log.debug("🔧 [내부 서비스] 권한 검증 우회 - requestedUserId: {}", requestedUserId);
            return;
        }

        // 일반 사용자인 경우 권한 검증
        Long currentUserId = getCurrentAuthenticatedUserId();

        if (!currentUserId.equals(requestedUserId)) {
            log.warn("❌ [권한 거부] 다른 사용자 정보 접근 시도 - currentUserId: {}, requestedUserId: {}",
                    currentUserId, requestedUserId);
            throw new RuntimeException("다른 사용자의 정보에 접근할 수 없습니다.");
        }

        log.debug("✅ [권한 확인] 사용자 접근 권한 검증 완료 - userId: {}", currentUserId);
    }
}
