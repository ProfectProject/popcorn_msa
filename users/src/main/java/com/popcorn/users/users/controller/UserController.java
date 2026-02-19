package com.popcorn.users.users.controller;

import java.util.List;
import java.util.UUID;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.popcorn.common.filter.PassportPrincipal;
import com.popcorn.users.auth.dto.CustomUserDetails;
import com.popcorn.users.users.dto.SignupRequest;
import com.popcorn.users.users.dto.SignupResponse;
import com.popcorn.users.users.dto.UserAddressRequest;
import com.popcorn.users.users.dto.UserAddressResponse;
import com.popcorn.users.users.dto.UserResponse;
import com.popcorn.users.users.dto.UserUpdateRequest;
import com.popcorn.users.users.entity.User;
import com.popcorn.users.users.entity.UserAddress;
import com.popcorn.users.users.service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;


@Tag(name = "User", description = "사용자 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/v1/users")
public class UserController {
    private final UserService userService;

    @Operation(
        summary = "회원가입",
        description = """
            새로운 사용자 계정을 생성합니다.

            **주요 기능:**
            - 이메일 중복 검증
            - 비밀번호 암호화 저장
            - 사용자 역할 선택 (CUSTOMER, OWNER, MANAGER)
            - 계정 활성화 상태로 생성

            **입력 검증:**
            - 이메일: 유효한 형식 & 중복 불가
            - 비밀번호: 최소 8자 이상
            - 비밀번호 확인: 비밀번호와 일치해야 함
            - 이름: 필수 입력
            - 전화번호: 11자리 숫자 (선택)
            - 역할: CUSTOMER, OWNER, MANAGER 중 선택

            **역할별 권한:**
            - CUSTOMER: 일반 고객 (주문, 예약)
            - OWNER: 사업자 (팝업 관리, 주문 관리)
            - MANAGER: 관리자 (매장 운영 지원)

            **사용 후 절차:**
            1. 회원가입 완료
            2. /api/v1/auth/login으로 로그인
            3. JWT 토큰으로 API 인증
            """
    )
    @ApiResponse(
        responseCode = "201",
        description = "회원가입 성공",
        content = @Content(
            schema = @Schema(implementation = SignupResponse.class),
            examples = {
                @ExampleObject(
                    name = "고객 회원가입 성공",
                    summary = "CUSTOMER 역할 회원가입 성공",
                    value = """
                        {
                          "userId": 12345,
                          "email": "customer@example.com",
                          "name": "김고객",
                          "role": "CUSTOMER",
                          "message": "회원가입이 완료되었습니다."
                        }
                        """
                ),
                @ExampleObject(
                    name = "사업자 회원가입 성공",
                    summary = "OWNER 역할 회원가입 성공",
                    value = """
                        {
                          "userId": 67890,
                          "email": "popcorn5@popcorn.com",
                          "name": "홍길동",
                          "role": "OWNER",
                          "message": "회원가입이 완료되었습니다."
                        }
                        """
                )
            }
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "입력값 검증 실패",
        content = @Content(
            examples = @ExampleObject(
                name = "검증 실패",
                value = """
                    {
                      "code": 400,
                      "message": "입력값이 올바르지 않습니다.",
                      "details": {
                        "email": "이미 사용 중인 이메일입니다.",
                        "password": "비밀번호는 최소 8자 이상이어야 합니다."
                      }
                    }
                    """
            )
        )
    )
    @PostMapping("/signup")
    public SignupResponse signup(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "회원가입 요청 정보",
            required = true,
            content = @Content(
                schema = @Schema(implementation = SignupRequest.class),
                examples = {
                    @ExampleObject(
                        name = "고객 회원가입",
                        summary = "고객(CUSTOMER) 역할 회원가입",
                        value = """
                            {
                              "email": "customer@example.com",
                              "password": "securePassword123",
                              "passwordCheck": "securePassword123",
                              "name": "김고객",
                              "phone": "01012345678",
                              "role": "CUSTOMER"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "사업자 회원가입",
                        summary = "사업자(OWNER) 역할 회원가입",
                        value = """
                            {
                              "email": "popcorn5@popcorn.com",
                              "password": "testPassword123",
                              "passwordCheck": "testPassword123",
                              "name": "홍길동",
                              "phone": "01012345678",
                              "role": "OWNER"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "관리자 회원가입",
                        summary = "관리자(MANAGER) 역할 회원가입",
                        value = """
                            {
                              "email": "manager@example.com",
                              "password": "managerPassword123",
                              "passwordCheck": "managerPassword123",
                              "name": "이관리",
                              "phone": "01087654321",
                              "role": "MANAGER"
                            }
                            """
                    )
                }
            )
        )
        @Valid @RequestBody SignupRequest request) {

        return userService.register(request);
    }

    @Operation(
        summary = "내 정보 조회",
        description = """
            현재 로그인된 사용자의 개인정보를 조회합니다.

            **인증 요구:**
            - JWT 토큰 필수 (Authorization: Bearer {token})

            **응답 정보:**
            - 사용자 ID, 이메일, 이름
            - 전화번호, 권한 정보
            - 계정 활성화 상태
            - 가입/수정 일시

            **사용 케이스:**
            - 마이페이지 화면 표시
            - 프로필 편집 전 현재 정보 확인
            - 권한 확인용
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "내 정보 조회 성공",
        content = @Content(
            schema = @Schema(implementation = UserResponse.class),
            examples = @ExampleObject(
                name = "내 정보",
                value = """
                    {
                      "userId": 1,
                      "email": "popcorn1@popcorn.com",
                      "name": "PopCorn Test User",
                      "phone": "01012345678",
                      "role": "CUSTOMER",
                      "isActive": true,
                      "createdAt": "2025-01-08T10:00:00",
                      "updatedAt": "2025-01-08T10:00:00"
                    }
                    """
            )
        )
    )
    @ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content(
            examples = @ExampleObject(
                name = "인증 실패",
                value = """
                    {
                      "code": 401,
                      "message": "인증이 필요합니다."
                    }
                    """
            )
        )
    )
    @SecurityRequirement(name = "Bearer Authentication")
    //@PreAuthorize("hasRole('CUSTOMER')")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER')")
    @GetMapping("/mypage")
    public UserResponse getMyInfo(@AuthenticationPrincipal PassportPrincipal principal) {
        // SecurityContext에서 userId 가져오기
        Long userId = principal.userId();
        System.out.println("gateway header에서 가져온 userId: " + userId);

        // DB에서 실제 유저 정보 조회
        User user = userService.getUserById(userId);

        return UserResponse.from(user);
    }

    /**
     * 내부 서비스용 사용자 조회 (userId 기반)
     */
    @GetMapping("/{userId}")
    public UserResponse getUserById(@PathVariable Long userId) {
        User user = userService.getUserById(userId);
        return UserResponse.from(user);
    }

    /**
     * 내부 서비스용 - 활성 CUSTOMER 사용자 ID 목록 조회
     */
    @GetMapping("/customer-ids")
    public List<Long> getCustomerUserIdsForInternal() {
        return userService.getAllActiveCustomerUserIds();
    }

    /**
     * 내부 서비스용 - 활성 사용자 존재 여부 확인
     */
    @GetMapping("/{userId}/exists")
    public Map<String, Boolean> existsUserForInternal(@PathVariable Long userId) {
        return Map.of("exists", userService.existsActiveUser(userId));
    }


    @Operation(
        summary = "내 정보 수정",
        description = """
            현재 로그인된 사용자의 개인정보를 수정합니다.

            **인증 요구:**
            - JWT 토큰 필수 (Authorization: Bearer {token})

            **수정 가능 항목:**
            - 이름: 2-50자 한글/영문
            - 전화번호: 11자리 숫자 (선택사항)
            - 비밀번호: 최소 8자 이상 (선택사항)

            **수정 불가 항목:**
            - 이메일 (계정 식별자로 고정)
            - 권한 (시스템 관리)
            - 사용자 ID

            **보안:**
            - 비밀번호 변경 시 암호화 저장
            - 수정된 정보는 즉시 반영
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "내 정보 수정 성공",
        content = @Content(
            schema = @Schema(implementation = UserResponse.class),
            examples = @ExampleObject(
                name = "수정 성공",
                value = """
                    {
                      "userId": 1,
                      "email": "popcorn1@popcorn.com",
                      "name": "수정된 이름",
                      "phone": "01087654321",
                      "role": "CUSTOMER",
                      "isActive": true,
                      "createdAt": "2025-01-08T10:00:00",
                      "updatedAt": "2025-01-08T15:30:00"
                    }
                    """
            )
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "입력값 검증 실패",
        content = @Content(
            examples = @ExampleObject(
                name = "검증 실패",
                value = """
                    {
                      "code": 400,
                      "message": "입력값이 올바르지 않습니다.",
                      "details": {
                        "name": "이름은 2자 이상 50자 이하여야 합니다.",
                        "phone": "전화번호는 11자리 숫자여야 합니다."
                      }
                    }
                    """
            )
        )
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER')")
    @PutMapping("/mypage")
    public UserResponse updateMyInfo(
        @AuthenticationPrincipal PassportPrincipal principal,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "수정할 사용자 정보",
            required = true,
            content = @Content(
                schema = @Schema(implementation = UserUpdateRequest.class),
                examples = @ExampleObject(
                    name = "정보 수정 요청",
                    value = """
                        {
                          "name": "수정된 이름",
                          "phone": "01087654321"
                        }
                        """
                )
            )
        )
        @Valid @RequestBody UserUpdateRequest request) {
        // SecurityContext에서 userId 가져오기
        Long userId = principal.userId();
        System.out.println("SecurityContext에서 가져온 userId: " + userId);

        User updatedUser = userService.updateUser(userId, request);
        return UserResponse.from(updatedUser);
    }

    /**
     * 사용자 계정 탈퇴
     */
    @Operation(
        summary = "내 계정 삭제(탈퇴)",
        description = """
            현재 로그인된 사용자의 계정을 삭제(탈퇴)합니다.

            **인증 요구:**
            - JWT 토큰 필수 (Authorization: Bearer {token})

            **응답 정보:**
            - 204 No Content (반환 데이터 없음)

            **사용 케이스:**
            - 회원이 직접 계정 탈퇴를 원할 때
            - 서비스 이용 중지 시
            """
    )
    @ApiResponse(
        responseCode = "204",
        description = "계정 삭제(탈퇴) 성공",
        content = @Content( // 204는 바디가 없으므로 content는 비워두는 것이 더 표준적이지만, 유지해도 무방
            schema = @Schema(hidden = true)
        )
    )
    @ApiResponse(
        responseCode = "401",
        description = "인증 필요",
        content = @Content(
            examples = @ExampleObject(
                name = "인증 실패",
                value = """
                    {
                    "code": 401,
                    "message": "인증이 필요합니다."
                    }
                    """
            )
        )
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER')") 
    @DeleteMapping("/me/deactivate")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal PassportPrincipal principal) {
        userService.deactivateUser(principal.userId()); // 메서드명도 delete로 변경 권장
        return ResponseEntity.noContent().build(); // 204
    }


    // 주소 목록 조회
    @Operation(
    summary = "내 주소 목록 조회",
    description = """
        현재 로그인한 사용자의 전체 주소 목록을 조회합니다.

        **인증 필수**
        - JWT 토큰 필요 (Authorization: Bearer {token})

        **반환 정보**
        - 주소 ID(UUID)
        - 주소명(addrName)
        - 기본주소(address1)
        - 상세주소(address2)
        - 우편번호(postalCode)
        - 기본주소 여부(isDefault)
        """
    )
    @ApiResponse(
        responseCode = "200",
        description = "주소 목록 조회 성공",
        content = @Content(
            schema = @Schema(implementation = UserAddressResponse.class),
            examples = @ExampleObject(
                name = "조회 성공",
                value = """
                    [
                    {
                        "addressId": "b6c1d7ea-4b1b-4fa2-9ad1-53b30a7f6abc",
                        "addrName": "집",
                        "address1": "서울 강남구 테헤란로 123",
                        "address2": "101동 1001호",
                        "postalCode": "06236",
                        "isDefault": true
                    }
                    ]
                    """
            )
        )
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me/address")
    public List<UserAddressResponse> getUserAddresses(@AuthenticationPrincipal PassportPrincipal principal){
        Long userId = principal.userId();
        List<UserAddress> addresses = userService.getUserAddresses(userId);
        return addresses.stream()
                .map(UserAddressResponse::from)
                .toList();
    }

    /**
     * 내부 서비스용 주소 조회 (userId 기반)
     */
    @GetMapping("/{userId}/addresses")
    public List<UserAddressResponse> getUserAddressesByUserId(@PathVariable Long userId) {
        List<UserAddress> addresses = userService.getUserAddresses(userId);
        return addresses.stream()
                .map(UserAddressResponse::from)
                .toList();
    }

    // 주소 
    @Operation(
    summary = "내 주소 등록",
    description = """
        새로운 주소를 등록합니다.

        **인증 필수**
        - JWT 토큰 필요

        **입력 예시**
        {
          "addrName": "회사",
          "address1": "서울 강남구 역삼동 111",
          "address2": "4층",
          "postalCode": "06250",
          "isDefault": false
        }

        **기능**
        - 기본주소(isDefault=true)를 생성하면 기존 기본 주소는 false 처리됨
        """
    )
    @ApiResponse(
        responseCode = "201",
        description = "주소 등록 성공",
        content = @Content(
            schema = @Schema(implementation = UserAddressResponse.class),
            examples = @ExampleObject(
                value = """
                    {
                    "addressId": "d21e9a2b-981a-48c0-9122-e8cdf32a03ce",
                    "addrName": "회사",
                    "address1": "서울 강남구 역삼동 111",
                    "address2": "4층",
                    "postalCode": "06250",
                    "isDefault": false
                    }
                    """
            )
        )
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/me/addresses")
    public UserAddressResponse createUserAddress(@AuthenticationPrincipal PassportPrincipal principal, @RequestBody UserAddressRequest request) {
        Long userId = principal.userId();
        UserAddress address = userService.createUserAddress(userId, request);
        return UserAddressResponse.from(address);
    }

    // 주소 수정
    @Operation(
    summary = "내 주소 수정",
    description = """
        기존 주소를 수정합니다.

        **인증 필수**
        - JWT 토큰 필요

        **수정 가능 항목**
        - addrName
        - address1
        - address2
        - postalCode
        - isDefault (true로 변경 시 기존 기본주소는 false 처리)
        """
    )
    @ApiResponse(
        responseCode = "200",
        description = "주소 수정 성공",
        content = @Content(
            schema = @Schema(implementation = UserAddressResponse.class),
            examples = @ExampleObject(
                value = """
                    {
                    "addressId": "085bd510-87db-4b8c-9613-17f77bb02d92",
                    "addrName": "집2",
                    "address1": "서울 송파구 올림픽로 240",
                    "address2": "20층",
                    "postalCode": "05554",
                    "isDefault": true
                    }
                    """
            )
        )
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PutMapping("me/addresses/{addressId}")
    public UserAddressResponse updateUserAddress(@AuthenticationPrincipal PassportPrincipal principal, @PathVariable UUID addressId, @RequestBody UserAddressRequest request) {
        Long userId = principal.userId();
        UserAddress address = userService.updateUserAddress(userId, addressId, request);
        return UserAddressResponse.from(address);
    }

    // 주소 삭제
    @Operation(
        summary = "내 주소 삭제",
        description = """
            로그인된 사용자의 특정 주소를 삭제합니다.

            **인증 요구:**
            - JWT 토큰 필수
            """
    )
    @ApiResponse(
            responseCode = "204",
            description = "주소 삭제 성공",
            content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
            responseCode = "401",
            description = "인증 필요"
    )
    @ApiResponse(
            responseCode = "404",
            description = "주소를 찾을 수 없음"
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping("/me/addresses/{addressId}")
    public void deleteUserAddress(@AuthenticationPrincipal PassportPrincipal principal, @PathVariable UUID addressId) {
        Long userId = principal.userId();
        userService.deleteUserAddress(userId, addressId);
    }

    // 기존 주소 설정
    @Operation(
        summary = "기본 주소 설정",
        description = """
            로그인된 사용자의 특정 주소를 기본 주소로 설정합니다.

            **인증 요구:**
            - JWT 토큰 필수
            """
    )
    @ApiResponse(
            responseCode = "200",
            description = "기본 주소 설정 성공"
    )
    @ApiResponse(
            responseCode = "401",
            description = "인증 필요"
    )
    @ApiResponse(
            responseCode = "404",
            description = "주소를 찾을 수 없음"
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PutMapping("/me/addresses/{addressId}/default")
    public UserAddressResponse setDefaultAddress(@AuthenticationPrincipal PassportPrincipal principal, @PathVariable UUID addressId) {
        Long userId =principal.userId();
        UserAddress address = userService.setDefaultAddress(userId, addressId);
        return UserAddressResponse.from(address);
    }

}
