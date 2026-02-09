package com.popcorn.store.inventory.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryRedisHoldServiceTest {

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @Mock
    RedisScript<Long> holdGoodsScript;

    @Mock
    RedisScript<Long> holdBothScript;

    @Mock
    RedisScript<Long> holdScheduleScript;

    @Mock
    RedisScript<Long> releaseHoldScript;

    InventoryRedisHoldService holdService;

    @BeforeEach
    void setUp() {
        holdService = new InventoryRedisHoldService(redisTemplate, holdScheduleScript, holdGoodsScript,
                holdBothScript, releaseHoldScript);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void holdGoods_shouldIncludeGoodsIdsInArgs() {
        UUID orderId = UUID.randomUUID();
        UUID popupId = UUID.randomUUID();
        List<InventoryRedisHoldService.GoodsHoldItem> items = List.of(
                new InventoryRedisHoldService.GoodsHoldItem(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1),
                new InventoryRedisHoldService.GoodsHoldItem(UUID.fromString("00000000-0000-0000-0000-000000000002"), 3)
        );

        when(redisTemplate.execute(eq(holdGoodsScript), anyList(), any(Object[].class))).thenReturn(1L);

        holdService.holdGoods(orderId, popupId, items);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(eq(holdGoodsScript), anyList(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();

        assertThat(args[0]).isEqualTo(orderId.toString());
        assertThat(args[1]).isEqualTo(popupId.toString());
        assertThat(args[2]).isEqualTo(String.valueOf(Duration.ofMinutes(30).toMillis()));
        assertThat(args[3]).isNotNull();
        assertThat(args[4]).isEqualTo("00000000-0000-0000-0000-000000000001|00000000-0000-0000-0000-000000000002");
        assertThat(args[5]).isEqualTo("1|3");
    }

    @Test
    void holdBoth_shouldIncludeGoodsIdsForCombinedHold() {
        UUID orderId = UUID.randomUUID();
        UUID popupId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        InventoryRedisHoldService.GoodsHoldItem one = new InventoryRedisHoldService.GoodsHoldItem(
                UUID.fromString("00000000-0000-0000-0000-000000000003"), 2);
        List<InventoryRedisHoldService.GoodsHoldItem> items = List.of(one);

        when(redisTemplate.execute(eq(holdBothScript), anyList(), any(Object[].class))).thenReturn(1L);

        holdService.holdBoth(orderId, popupId, scheduleId, 2, items);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(eq(holdBothScript), anyList(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();

        assertThat(args[0]).isEqualTo(orderId.toString());
        assertThat(args[1]).isEqualTo(popupId.toString());
        assertThat(args[2]).isEqualTo(scheduleId.toString());
        assertThat(args[3]).isEqualTo("2");
        assertThat(args[4]).isEqualTo("00000000-0000-0000-0000-000000000003");
        assertThat(args[5]).isEqualTo("2");
        assertThat(args[6]).isEqualTo(String.valueOf(Duration.ofMinutes(30).toMillis()));
        assertThat(args[7]).isNotNull();
    }
}
