/*package com.popcorn.store.domain.popup.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.popcorn.common.dto.BaseError;
import com.popcorn.common.dto.BaseResponse;
import com.popcorn.common.dto.CommonResponseCode;
import com.popcorn.store.domain.popup.dto.PopupResponseCode;
import com.popcorn.store.domain.popup.exception.PopupException;

class PopupExceptionHandlerTest {

	@Test
	void handlesPopupNotFound() {
		PopupExceptionHandler handler = new PopupExceptionHandler();

		ResponseEntity<BaseResponse<BaseError>> response = handler.handleBaseException(PopupException.popupNotFound());

		assertThat(response.getStatusCode().value()).isEqualTo(PopupResponseCode.POPUP_NOT_FOUND.getHttpStatus());
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getData().getDetail()).isEqualTo("팝업 정보를 찾을 수 없습니다.");
	}

	@Test
	void handlesInvalidRequestMessage() {
		PopupExceptionHandler handler = new PopupExceptionHandler();
		PopupException exception = new PopupException(PopupResponseCode.INVALID_REQUEST);

		ResponseEntity<BaseResponse<BaseError>> response = handler.handleBaseException(exception);

		assertThat(response.getStatusCode().value()).isEqualTo(PopupResponseCode.INVALID_REQUEST.getHttpStatus());
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getData().getDetail()).isEqualTo("요청을 확인해 주세요.");
	}

	@Test
	void handlesValidationException() throws Exception {
		PopupExceptionHandler handler = new PopupExceptionHandler();

		MethodParameter parameter = new MethodParameter(
				PopupExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0);
		BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
		bindingResult.addError(new FieldError("request", "name", "required"));
		MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

		ResponseEntity<BaseResponse<BaseError>> response = handler.handleValidationException(exception);

		assertThat(response.getStatusCode().value()).isEqualTo(CommonResponseCode.INVALID_REQUEST.getHttpStatus());
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getData().getDetail()).isEqualTo("name: required");
	}

	@Test
	void handlesBindException() {
		PopupExceptionHandler handler = new PopupExceptionHandler();

		BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
		bindingResult.addError(new FieldError("request", "size", "invalid"));
		BindException exception = new BindException(bindingResult);

		ResponseEntity<BaseResponse<BaseError>> response = handler.handleBindException(exception);

		assertThat(response.getStatusCode().value()).isEqualTo(CommonResponseCode.INVALID_REQUEST.getHttpStatus());
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getData().getDetail()).isEqualTo("size: invalid");
	}

	@Test
	void handlesTypeMismatchException() throws Exception {
		PopupExceptionHandler handler = new PopupExceptionHandler();

		MethodParameter parameter = new MethodParameter(
				PopupExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0);
		MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
				"abc", Integer.class, "size", parameter, new IllegalArgumentException("bad"));

		ResponseEntity<BaseResponse<BaseError>> response = handler.handleTypeMismatchException(exception);

		assertThat(response.getStatusCode().value()).isEqualTo(CommonResponseCode.INVALID_REQUEST.getHttpStatus());
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getData().getDetail()).contains("Integer").contains("abc");
	}

	private static void dummy(String input) {
		// no-op
	}
}
*/