package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AddressStatus;
import com.erc20.platform.dal.mapper.UserAddressMapper;
import com.erc20.platform.domain.entity.UserAddress;
import com.erc20.platform.service.dto.GeneratedAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock private UserAddressMapper userAddressMapper;
    @Mock private HdWalletService hdWalletService;

    private AddressService addressService;

    private static final String USER_ID = "user001";
    private static final Long TOKEN_ID = 1L;
    private static final String POOL_ADDRESS = "0xabc123def456abc123def456abc123def456abc1";

    @BeforeEach
    void setUp() {
        addressService = new AddressService(userAddressMapper, hdWalletService);
    }

    // --- 1.1 allocateDepositAddress for new user → returns address, status=BOUND, user_id set ---

    @Test
    void allocateDepositAddress_newUser_returnsAddressAndStatusBound() {
        UserAddress poolAddress = UserAddress.builder()
                .id(10L)
                .address(POOL_ADDRESS)
                .privateKeyEnc("enc_key_1")
                .addressIndex(0)
                .status(AddressStatus.AVAILABLE.getCode())
                .build();

        // 1st call: check existing bound address → null (new user)
        // 2nd call: pick available from pool → returns pool address
        doReturn(null).doReturn(poolAddress)
                .when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(userAddressMapper).updateById(any(UserAddress.class));

        String address = addressService.allocateDepositAddress(USER_ID, TOKEN_ID);

        assertNotNull(address);
        assertEquals(POOL_ADDRESS, address);

        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(userAddressMapper).updateById(captor.capture());
        UserAddress updated = captor.getValue();
        assertEquals(AddressStatus.BOUND.getCode(), updated.getStatus());
        assertEquals(USER_ID, updated.getUserId());
        assertEquals(TOKEN_ID, updated.getTokenId());
    }

    // --- 1.2 allocate for user who already has address for same token → returns same address ---

    @Test
    void allocateDepositAddress_existingUser_returnsSameAddress() {
        UserAddress existing = UserAddress.builder()
                .id(10L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .address(POOL_ADDRESS)
                .status(AddressStatus.BOUND.getCode())
                .build();
        doReturn(existing).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));

        String address = addressService.allocateDepositAddress(USER_ID, TOKEN_ID);

        assertEquals(POOL_ADDRESS, address);
        verify(userAddressMapper, never()).updateById(any());
    }

    // --- 1.3 findByAddress: known → returns UserAddress; unknown → Optional.empty() ---

    @Test
    void findByAddress_knownAddress_returnsUserAddress() {
        UserAddress expected = UserAddress.builder()
                .id(10L)
                .userId(USER_ID)
                .address(POOL_ADDRESS)
                .status(AddressStatus.BOUND.getCode())
                .build();
        doReturn(expected).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));

        Optional<UserAddress> result = addressService.findByAddress("0xABC123DEF456ABC123DEF456ABC123DEF456ABC1");

        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getUserId());
    }

    @Test
    void findByAddress_unknownAddress_returnsEmpty() {
        doReturn(null).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));

        Optional<UserAddress> result = addressService.findByAddress("0x0000000000000000000000000000000000000000");

        assertFalse(result.isPresent());
    }

    // --- 1.4 pool empty → triggers preGenerateAddresses, then allocation succeeds ---

    @Test
    void allocateDepositAddress_poolEmpty_preGeneratesThenAllocates() {
        UserAddress generatedAddress = UserAddress.builder()
                .id(1L)
                .address("0x1111111111111111111111111111111111111111")
                .privateKeyEnc("enc_key_0")
                .addressIndex(0)
                .status(AddressStatus.AVAILABLE.getCode())
                .build();

        // 1st: no existing bound address
        // 2nd: pool empty (no available address)
        // 3rd: after preGenerate, available address found
        doReturn(null).doReturn(null).doReturn(generatedAddress)
                .when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));

        doReturn(GeneratedAddress.builder()
                .address("0x1111111111111111111111111111111111111111")
                .privateKeyEnc("enc_key_0")
                .build())
                .when(hdWalletService).derive(anyInt());
        doReturn(0L).when(userAddressMapper).selectCount(any(LambdaQueryWrapper.class));
        doReturn(1).when(userAddressMapper).insert(any(UserAddress.class));
        doReturn(1).when(userAddressMapper).updateById(any(UserAddress.class));

        String address = addressService.allocateDepositAddress(USER_ID, TOKEN_ID);

        assertNotNull(address);
        verify(hdWalletService, atLeastOnce()).derive(anyInt());
        verify(userAddressMapper, atLeastOnce()).insert(any(UserAddress.class));
    }
}
