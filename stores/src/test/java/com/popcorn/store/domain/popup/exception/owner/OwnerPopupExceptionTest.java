/*package com.popcorn.store.domain.popup.exception.owner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.popcorn.store.domain.popup.dto.owner.OwnerPopupResponseCode;

class OwnerPopupExceptionTest {

	@Test
	@DisplayName("OwnerPopupException factory 메서드가 동작한다")
	void ownerPopupExceptionFactories() {
		assertThat(OwnerPopupException.unauthenticated().getResponseCode())
				.isEqualTo(OwnerPopupResponseCode.UNAUTHENTICATED);
		assertThat(OwnerPopupException.userIdRequired().getResponseCode())
				.isEqualTo(OwnerPopupResponseCode.USER_ID_REQUIRED);
		assertThat(OwnerPopupException.invalidPrincipal().getResponseCode())
				.isEqualTo(OwnerPopupResponseCode.INVALID_PRINCIPAL);
		assertThat(OwnerPopupException.invalidRole().getResponseCode())
				.isEqualTo(OwnerPopupResponseCode.INVALID_ROLE);
		assertThat(OwnerPopupException.notOwner().getResponseCode())
				.isEqualTo(OwnerPopupResponseCode.USER_NOT_OWNER);
		assertThat(OwnerPopupException.of(OwnerPopupResponseCode.USER_NOT_OWNER).getResponseCode())
				.isEqualTo(OwnerPopupResponseCode.USER_NOT_OWNER);
	}
}*/
