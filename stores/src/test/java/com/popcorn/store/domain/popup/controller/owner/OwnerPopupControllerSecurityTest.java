/*package com.popcorn.store.domain.popup.controller.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import com.popcorn.store.domain.popup.exception.owner.OwnerPopupException;
import com.popcorn.store.domain.popup.service.owner.OwnerPopupService;

class OwnerPopupControllerSecurityTest {

	@Test
	@DisplayName("인증 정보가 없으면 예외가 발생한다")
	void unauthenticatedWhenAuthenticationMissing() {
		OwnerPopupController controller = new OwnerPopupController(Mockito.mock(OwnerPopupService.class));
		assertThatThrownBy(() -> invokeOwnerId(controller, null))
				.isInstanceOf(OwnerPopupException.class);
	}

	@Test
	@DisplayName("인증되지 않은 경우 예외가 발생한다")
	void unauthenticatedWhenNotAuthenticated() {
		OwnerPopupController controller = new OwnerPopupController(Mockito.mock(OwnerPopupService.class));
		Authentication authentication = Mockito.mock(Authentication.class);
		Mockito.when(authentication.isAuthenticated()).thenReturn(false);

		assertThatThrownBy(() -> invokeOwnerId(controller, authentication))
				.isInstanceOf(OwnerPopupException.class);
	}

	@Test
	@DisplayName("principal이 누락되면 예외가 발생한다")
	void invalidPrincipalWhenMissing() {
		OwnerPopupController controller = new OwnerPopupController(Mockito.mock(OwnerPopupService.class));
		Authentication authentication = Mockito.mock(Authentication.class);
		Mockito.when(authentication.isAuthenticated()).thenReturn(true);
		Mockito.when(authentication.getPrincipal()).thenReturn(null);

		assertThatThrownBy(() -> invokeOwnerId(controller, authentication))
				.isInstanceOf(OwnerPopupException.class);
	}

	@Test
	@DisplayName("principal과 name이 모두 유효하지 않으면 예외가 발생한다")
	void invalidPrincipalWhenNameInvalid() {
		OwnerPopupController controller = new OwnerPopupController(Mockito.mock(OwnerPopupService.class));
		Authentication authentication = Mockito.mock(Authentication.class);
		Mockito.when(authentication.isAuthenticated()).thenReturn(true);
		Mockito.when(authentication.getName()).thenReturn("not-number");
		Mockito.when(authentication.getPrincipal()).thenReturn(new Object());

		assertThatThrownBy(() -> invokeOwnerId(controller, authentication))
				.isInstanceOf(OwnerPopupException.class);
	}

	@Test
	@DisplayName("사용자 ID가 없으면 예외가 발생한다")
	void userIdRequiredWhenMissing() {
		OwnerPopupController controller = new OwnerPopupController(Mockito.mock(OwnerPopupService.class));
		Authentication authentication = Mockito.mock(Authentication.class);
		Mockito.when(authentication.isAuthenticated()).thenReturn(true);
		Mockito.when(authentication.getName()).thenReturn(null);
		Mockito.when(authentication.getPrincipal()).thenReturn(new Object());

		assertThatThrownBy(() -> invokeOwnerId(controller, authentication))
				.isInstanceOf(OwnerPopupException.class);
	}

	@Test
	@DisplayName("권한 문자열이 유효하지 않으면 예외가 발생한다")
	void invalidRoleWhenAuthorityUnknown() {
		OwnerPopupController controller = new OwnerPopupController(Mockito.mock(OwnerPopupService.class));
		Authentication authentication = Mockito.mock(Authentication.class);
		Mockito.when(authentication.isAuthenticated()).thenReturn(true);
		Mockito.when(authentication.getName()).thenReturn("1001");
		Mockito.when(authentication.getPrincipal()).thenReturn(1001L);
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_INVALID"));
		Mockito.when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);

		assertThatThrownBy(() -> invokeOwnerId(controller, authentication))
				.isInstanceOf(OwnerPopupException.class);
	}

	@Test
	@DisplayName("OWNER가 아니면 예외가 발생한다")
	void notOwnerWhenRoleIsNotOwner() {
		OwnerPopupController controller = new OwnerPopupController(Mockito.mock(OwnerPopupService.class));
		Authentication authentication = Mockito.mock(Authentication.class);
		Mockito.when(authentication.isAuthenticated()).thenReturn(true);
		Mockito.when(authentication.getName()).thenReturn("10");
		Mockito.when(authentication.getPrincipal()).thenReturn(10L);
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_MANAGER"));
		Mockito.when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);

		assertThatThrownBy(() -> invokeOwnerId(controller, authentication))
				.isInstanceOf(OwnerPopupException.class);
	}

	@Test
	@DisplayName("OWNER는 정상적으로 userId를 반환한다")
	void returnsOwnerIdForValidOwner() {
		OwnerPopupController controller = new OwnerPopupController(Mockito.mock(OwnerPopupService.class));
		Authentication authentication = Mockito.mock(Authentication.class);
		Mockito.when(authentication.isAuthenticated()).thenReturn(true);
		Mockito.when(authentication.getName()).thenReturn("55");
		Mockito.when(authentication.getPrincipal()).thenReturn(55L);
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_OWNER"));
		Mockito.when(authentication.getAuthorities()).thenAnswer(invocation -> authorities);

		Long ownerId = invokeOwnerId(controller, authentication);
		assertThat(ownerId).isEqualTo(55L);
	}

	private Long invokeOwnerId(OwnerPopupController controller, Authentication authentication) {
		return ReflectionTestUtils.invokeMethod(controller, "getCurrentOwnerId", authentication);
	}

}
*/