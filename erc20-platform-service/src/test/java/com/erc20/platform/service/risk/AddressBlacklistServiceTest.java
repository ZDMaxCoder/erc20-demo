package com.erc20.platform.service.risk;

import com.erc20.platform.dal.mapper.AddressBlacklistMapper;
import com.erc20.platform.domain.entity.AddressBlacklist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressBlacklistServiceTest {

    @Mock private RedissonClient redissonClient;
    @Mock private AddressBlacklistMapper blacklistMapper;
    @Mock private RSet<String> blacklistSet;

    private AddressBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        blacklistService = new AddressBlacklistService(redissonClient, blacklistMapper);
    }

    // --- 6.1 add address → isBlacklisted returns true. Remove → returns false ---

    @Test
    void addToBlacklist_then_isBlacklisted_returnsTrue() {
        doReturn(blacklistSet).when(redissonClient).getSet(anyString());
        doReturn(true).when(blacklistSet).contains("0xabc123");
        doReturn(1).when(blacklistMapper).insert(any(AddressBlacklist.class));

        blacklistService.addToBlacklist("0xabc123", "suspicious", "admin");
        boolean result = blacklistService.isBlacklisted("0xabc123");

        assertTrue(result);
        verify(blacklistSet).add("0xabc123");

        ArgumentCaptor<AddressBlacklist> captor = ArgumentCaptor.forClass(AddressBlacklist.class);
        verify(blacklistMapper).insert(captor.capture());
        assertEquals("0xabc123", captor.getValue().getAddress());
        assertEquals("suspicious", captor.getValue().getReason());
        assertEquals("admin", captor.getValue().getOperator());
    }

    @Test
    void removeFromBlacklist_then_isBlacklisted_returnsFalse() {
        doReturn(blacklistSet).when(redissonClient).getSet(anyString());
        doReturn(false).when(blacklistSet).contains("0xabc123");

        blacklistService.removeFromBlacklist("0xabc123");
        boolean result = blacklistService.isBlacklisted("0xabc123");

        assertFalse(result);
        verify(blacklistSet).remove("0xabc123");
    }

    // --- 6.2 address normalization (mixed case treated same as lowercase) ---

    @Test
    void isBlacklisted_mixedCase_treatedAsLowercase() {
        doReturn(blacklistSet).when(redissonClient).getSet(anyString());
        doReturn(true).when(blacklistSet).contains("0xabc123");

        boolean result = blacklistService.isBlacklisted("0xABC123");

        assertTrue(result);
        verify(blacklistSet).contains("0xabc123");
    }

    @Test
    void addToBlacklist_mixedCase_storedAsLowercase() {
        doReturn(blacklistSet).when(redissonClient).getSet(anyString());
        doReturn(1).when(blacklistMapper).insert(any(AddressBlacklist.class));

        blacklistService.addToBlacklist("0xABC123", "test", "admin");

        verify(blacklistSet).add("0xabc123");

        ArgumentCaptor<AddressBlacklist> captor = ArgumentCaptor.forClass(AddressBlacklist.class);
        verify(blacklistMapper).insert(captor.capture());
        assertEquals("0xabc123", captor.getValue().getAddress());
    }
}
