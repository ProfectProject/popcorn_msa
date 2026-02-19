/*package com.popcorn.checkIns.checkin.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popcorn.checkIns.checkin.controller.CheckinExceptionHandler;
import com.popcorn.checkIns.checkin.dto.response.CheckinDetailResponse;
import com.popcorn.checkIns.checkin.dto.response.CheckinListResponse;
import com.popcorn.checkIns.checkin.exception.CheckinException;
import com.popcorn.checkIns.checkin.service.CheckinService;
import com.popcorn.common.config.CommonConfig;

class CheckinControllerTest {

	private MockMvc mockMvc;
	private CheckinService checkinService;

	@BeforeEach
	void setUp() {
		checkinService = Mockito.mock(CheckinService.class);
		ObjectMapper objectMapper = new CommonConfig().objectMapper();

		CheckinController controller = new CheckinController(checkinService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new CheckinExceptionHandler())
				.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
				.build();
	}

	@Test
	@DisplayName("체크인 목록 조회 성공")
	void getCheckins_success() throws Exception {
		UUID checkinId = UUID.fromString("90000000-0000-0000-0000-000000000101");
		UUID orderId = UUID.fromString("40000000-0000-0000-0000-000000000004");
		CheckinListResponse.Item item = CheckinListResponse.Item.builder()
				.checkinId(checkinId)
				.orderId(orderId)
				.qrCode("qr-list-001")
				.createdAt(LocalDateTime.now())
				.build();
		CheckinListResponse response = CheckinListResponse.builder()
				.count(1)
				.items(List.of(item))
				.build();

		Mockito.when(checkinService.getCheckins(50)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.count").value(1))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.items[0].checkinId").value(checkinId.toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.items[0].orderId").value(orderId.toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.items[0].qrCode").value("qr-list-001"));
	}

	@Test
	@DisplayName("체크인 상세 조회 성공")
	void getCheckin_success() throws Exception {
		UUID checkinId = UUID.fromString("90000000-0000-0000-0000-000000000102");
		UUID orderId = UUID.fromString("40000000-0000-0000-0000-000000000005");
		UUID qrId = UUID.fromString("80000000-0000-0000-0000-000000000001");
		CheckinDetailResponse response = CheckinDetailResponse.builder()
				.checkinId(checkinId)
				.orderId(orderId)
				.orderQrCodeId(qrId)
				.qrCode("qr-detail-001")
				.createdAt(LocalDateTime.now())
				.createdBy(1001L)
				.build();

		Mockito.when(checkinService.getCheckin(checkinId)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins/{checkinId}", checkinId))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.checkinId").value(checkinId.toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.orderId").value(orderId.toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.orderQrCodeId").value(qrId.toString()))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.qrCode").value("qr-detail-001"));
	}

	// @Test
	// @DisplayName("체크인 상세 조회 실패 - 체크인을 찾을 수 없음")
	// void getCheckin_fail_notFound() throws Exception {
	//	UUID checkinId = UUID.fromString("90000000-0000-0000-0000-000000000999");
	//	Mockito.when(checkinService.getCheckin(checkinId))
	//			.thenThrow(CheckinException.notFound());
	//
	//	mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins/{checkinId}", checkinId))
	//			.andExpect(MockMvcResultMatchers.status().isNotFound())
	//			.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(2100));
	// }

	@Test
	@DisplayName("체크인 목록 조회 - 빈 목록")
	void getCheckins_emptyList() throws Exception {
		CheckinListResponse response = CheckinListResponse.builder()
				.count(0)
				.items(List.of())
				.build();

		Mockito.when(checkinService.getCheckins(50)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.count").value(0))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.items").isEmpty());
	}

	@Test
	@DisplayName("체크인 목록 조회 - 파라미터 테스트")
	void getCheckins_withParams() throws Exception {
		CheckinListResponse response = CheckinListResponse.builder()
				.count(0)
				.items(List.of())
				.build();

		Mockito.when(checkinService.getCheckins(100)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins")
						.param("size", "100"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
				.andExpect(MockMvcResultMatchers.jsonPath("$.data.count").value(0));
	}

	@Test
	@DisplayName("체크인 목록 조회 - size 경계값 테스트 (0)")
	void getCheckins_withSizeZero() throws Exception {
		CheckinListResponse response = CheckinListResponse.builder()
				.count(0)
				.items(List.of())
				.build();

		// size=0이면 normalizeLimit에서 1로 정규화됨
		Mockito.when(checkinService.getCheckins(1)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins")
						.param("size", "0"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
	}

	@Test
	@DisplayName("체크인 목록 조회 - size 음수 테스트")
	void getCheckins_withNegativeSize() throws Exception {
		CheckinListResponse response = CheckinListResponse.builder()
				.count(0)
				.items(List.of())
				.build();

		// size=-1이면 normalizeLimit에서 1로 정규화됨
		Mockito.when(checkinService.getCheckins(1)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins")
						.param("size", "-1"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
	}

	@Test
	@DisplayName("체크인 목록 조회 - size 최댓값 초과 테스트")
	void getCheckins_withSizeExceedsMax() throws Exception {
		CheckinListResponse response = CheckinListResponse.builder()
				.count(0)
				.items(List.of())
				.build();

		// size=300이면 normalizeLimit에서 200으로 정규화됨
		Mockito.when(checkinService.getCheckins(200)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins")
						.param("size", "300"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
	}

	@Test
	@DisplayName("체크인 목록 조회 - size 경계값 테스트 (1)")
	void getCheckins_withSizeOne() throws Exception {
		CheckinListResponse response = CheckinListResponse.builder()
				.count(0)
				.items(List.of())
				.build();

		Mockito.when(checkinService.getCheckins(1)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins")
						.param("size", "1"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
	}

	@Test
	@DisplayName("체크인 목록 조회 - size 경계값 테스트 (200)")
	void getCheckins_withSizeTwoHundred() throws Exception {
		CheckinListResponse response = CheckinListResponse.builder()
				.count(0)
				.items(List.of())
				.build();

		Mockito.when(checkinService.getCheckins(200)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins")
						.param("size", "200"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
	}

	@Test
	@DisplayName("체크인 목록 조회 - size 경계값 테스트 (201)")
	void getCheckins_withSizeTwoHundredOne() throws Exception {
		CheckinListResponse response = CheckinListResponse.builder()
				.count(0)
				.items(List.of())
				.build();

		// size=201이면 normalizeLimit에서 200으로 정규화됨
		Mockito.when(checkinService.getCheckins(200)).thenReturn(response);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/checkins")
						.param("size", "201"))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200));
	}
}
*/