/*package com.popcorn.store.domain.popup.controller.owner;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.popcorn.common.dto.BaseError;
import com.popcorn.common.dto.BaseResponse;
import com.popcorn.store.domain.popup.exception.PopupException;
import com.popcorn.store.domain.popup.exception.owner.OwnerPopupException;
import com.popcorn.store.domain.store.exception.StoreException;

class OwnerPopupExceptionHandlerTest {

	@Test
	@DisplayName("오너 팝업 예외 핸들러는 각 예외를 처리한다")
	void handlesOwnerPopupExceptions() throws Exception {
		OwnerPopupExceptionHandler handler = new OwnerPopupExceptionHandler();

		ResponseEntity<BaseResponse<BaseError>> popupResponse =
				handler.handlePopupException(PopupException.popupNotFound());
		assertThat(popupResponse.getBody().getData()).isNotNull();

		ResponseEntity<BaseResponse<BaseError>> storeResponse =
				handler.handleStoreException(StoreException.storeNotFound("store"));
		assertThat(storeResponse.getBody().getData()).isNotNull();

		ResponseEntity<BaseResponse<BaseError>> ownerResponse =
				handler.handleOwnerPopupException(OwnerPopupException.notOwner());
		assertThat(ownerResponse.getBody().getData()).isNotNull();

		Method method = OwnerPopupExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class);
		MethodParameter parameter = new MethodParameter(method, 0);
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
		bindingResult.addError(new FieldError("request", "field", "invalid"));
		MethodArgumentNotValidException validationException =
				new MethodArgumentNotValidException(parameter, bindingResult);

		ResponseEntity<BaseResponse<BaseError>> validationResponse =
				handler.handleValidationException(validationException);
		assertThat(validationResponse.getBody().getData().getDetail()).contains("field");

		BindException bindException = new BindException(bindingResult);
		ResponseEntity<BaseResponse<BaseError>> bindResponse =
				handler.handleBindException(bindException);
		assertThat(bindResponse.getBody().getData().getDetail()).contains("field");

		MethodArgumentTypeMismatchException mismatchException =
				new MethodArgumentTypeMismatchException("value", Integer.class, "size", parameter, new IllegalArgumentException());
		ResponseEntity<BaseResponse<BaseError>> mismatchResponse =
				handler.handleTypeMismatchException(mismatchException);
		assertThat(mismatchResponse.getBody().getData().getDetail()).contains("Integer");

		HttpInputMessage inputMessage = Mockito.mock(HttpInputMessage.class);
		ResponseEntity<BaseResponse<BaseError>> readResponse =
				handler.handleMessageNotReadable(new HttpMessageNotReadableException("bad", new RuntimeException("cause"), inputMessage));
		assertThat(readResponse.getBody().getData()).isNotNull();

		ResponseEntity<BaseResponse<BaseError>> generalResponse =
				handler.handleGeneralException(new RuntimeException("fail"));
		assertThat(generalResponse.getBody().getData()).isNotNull();
	}

	@Test
	@DisplayName("일반 바인딩 오류 메시지를 생성한다")
	void handlesMissingParameter() {
		OwnerPopupExceptionHandler handler = new OwnerPopupExceptionHandler();
		MissingServletRequestParameterException ex =
				new MissingServletRequestParameterException("storeId", "UUID");
		ResponseEntity<BaseResponse<BaseError>> response = handler.handleGeneralException(ex);
		assertThat(response.getBody().getData()).isNotNull();
	}

	@SuppressWarnings("unused")
	private void dummy(String value) {
	}
}
*/