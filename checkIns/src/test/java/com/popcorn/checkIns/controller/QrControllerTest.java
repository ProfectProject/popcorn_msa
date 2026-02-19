/*package com.popcorn.checkIns.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.checkIns.dto.response.QrCodeResponse;
import com.popcorn.checkIns.dto.response.QrVerifyResponse;
import com.popcorn.checkIns.exception.QrException;
import com.popcorn.checkIns.service.QrCodeService;
import com.popcorn.common.config.CommonConfig;

class QrControllerTest {

	private MockMvc mockMvc;
	private QrCodeService qrCodeService;

	@BeforeEach
	void setUp() {
		qrCodeService = Mockito.mock(QrCodeService.class);
		ObjectMapper objectMapper = new CommonConfig().objectMapper();

		QrController controller = new QrController(qrCodeService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new QrExceptionHandler())
				.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
				.build();
	}

	@Test
	@DisplayName("QR 발급 성공")
	void issueQr_success() throws Exception {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001001");
		QrCodeResponse response = QrCodeResponse.builder()
				.orderId(orderId)
				.qrCode("qr-test-001")
				.expiresAt(LocalDateTime.now().plusMinutes(10))
				.build();

		when(qrCodeService.issue(orderId)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.post("/api/qr/v1/orders/{orderId}", orderId))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.orderId").value(orderId.toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.qrCode").value("qr-test-001"));
	}

	@Test
	@DisplayName("QR 조회 성공")
	void getQr_success() throws Exception {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001002");
		QrCodeResponse response = QrCodeResponse.builder()
				.orderId(orderId)
				.qrCode("qr-test-002")
				.expiresAt(LocalDateTime.now().plusMinutes(10))
				.build();

		when(qrCodeService.get(orderId)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/qr/v1/orders/{orderId}", orderId))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.orderId").value(orderId.toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.qrCode").value("qr-test-002"));
	}

	@Test
	@DisplayName("QR 검증 성공")
	void verifyQr_success() throws Exception {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001003");
		UUID checkinId = UUID.fromString("90000000-0000-0000-0000-000000000001");
		QrVerifyResponse response = QrVerifyResponse.builder()
				.valid(true)
				.checkinId(checkinId)
				.orderId(orderId)
				.qrCode("qr-test-003")
				.expiresAt(LocalDateTime.now().plusMinutes(10))
				.build();

		when(qrCodeService.verify(any())).thenReturn(response);

		String requestJson = """
				{
					"qrCode": "qr-test-003"
				}
				""";

		mockMvc.perform(MockMvcRequestBuilders.post("/api/qr/v1/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.valid").value(true))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.orderId").value(orderId.toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.checkinId").value(checkinId.toString()));
	}

	@Test
	@DisplayName("QR 검증 실패 - 요청값 누락")
	void verifyQr_fail_blank() throws Exception {
		String requestJson = """
				{
					"qrCode": ""
				}
				""";

		mockMvc.perform(MockMvcRequestBuilders.post("/api/qr/v1/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(MockMvcResultMatchers.status().isBadRequest())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(400));
	}

	@Test
	@DisplayName("QR 발급 실패 - QR 코드 발급 예외")
	void issueQr_fail_exception() throws Exception {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001004");
		when(qrCodeService.issue(orderId)).thenThrow(QrException.orderNotFound());

		mockMvc.perform(MockMvcRequestBuilders.post("/api/qr/v1/orders/{orderId}", orderId))
				.andExpect(MockMvcResultMatchers.status().isNotFound())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(2002));
	}

	@Test
	@DisplayName("QR 조회 실패 - QR 코드를 찾을 수 없음")
	void getQr_fail_notFound() throws Exception {
		UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001005");
		when(qrCodeService.get(orderId)).thenThrow(QrException.qrNotFound());

		mockMvc.perform(MockMvcRequestBuilders.get("/api/qr/v1/orders/{orderId}", orderId))
				.andExpect(MockMvcResultMatchers.status().isNotFound())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(2000));
	}

	@Test
	@DisplayName("QR 검증 실패 - 유효하지 않은 QR 코드")
	void verifyQr_fail_invalid() throws Exception {
		when(qrCodeService.verify(any())).thenThrow(QrException.qrNotFound());

		String requestJson = """
				{
					"qrCode": "invalid-qr-code"
				}
				""";

		mockMvc.perform(MockMvcRequestBuilders.post("/api/qr/v1/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(MockMvcResultMatchers.status().isNotFound())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(2000));
	}

	@Test
	@DisplayName("QR 검증 실패 - 서버 에러")
	void verifyQr_fail_serverError() throws Exception {
		when(qrCodeService.verify(any())).thenThrow(new RuntimeException("예상치 못한 오류"));

		String requestJson = """
				{
					"qrCode": "test-qr-code"
				}
				""";

		mockMvc.perform(MockMvcRequestBuilders.post("/api/qr/v1/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJson))
				.andExpect(MockMvcResultMatchers.status().isInternalServerError())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(500));
	}
}
*/