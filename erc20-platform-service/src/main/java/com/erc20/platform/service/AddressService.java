package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AddressStatus;
import com.erc20.platform.common.util.AddressUtil;
import com.erc20.platform.dal.mapper.UserAddressMapper;
import com.erc20.platform.domain.entity.UserAddress;
import com.erc20.platform.service.dto.GeneratedAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class AddressService {

    private static final int DEFAULT_PRE_GENERATE_COUNT = 100;

    private final UserAddressMapper userAddressMapper;
    private final HdWalletService hdWalletService;

    public AddressService(UserAddressMapper userAddressMapper, HdWalletService hdWalletService) {
        this.userAddressMapper = userAddressMapper;
        this.hdWalletService = hdWalletService;
    }

    public String allocateDepositAddress(String userId, Long tokenId) {
        UserAddress existing = userAddressMapper.selectOne(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .eq(UserAddress::getTokenId, tokenId)
                        .eq(UserAddress::getStatus, AddressStatus.BOUND.getCode()));
        if (existing != null) {
            return existing.getAddress();
        }

        UserAddress available = pickAvailableAddress();
        if (available == null) {
            preGenerateAddresses(DEFAULT_PRE_GENERATE_COUNT);
            available = pickAvailableAddress();
        }

        available.setUserId(userId);
        available.setTokenId(tokenId);
        available.setStatus(AddressStatus.BOUND.getCode());
        available.setUpdatedAt(new Date());
        userAddressMapper.updateById(available);

        log.info("Allocated address {} to user {} for token {}", available.getAddress(), userId, tokenId);
        return available.getAddress();
    }

    public void preGenerateAddresses(int count) {
        Long maxIndex = userAddressMapper.selectCount(
                new LambdaQueryWrapper<UserAddress>());
        int startIndex = maxIndex == null ? 0 : maxIndex.intValue();

        for (int i = 0; i < count; i++) {
            int index = startIndex + i;
            GeneratedAddress generated = hdWalletService.derive(index);

            UserAddress address = UserAddress.builder()
                    .address(AddressUtil.normalize(generated.getAddress()))
                    .privateKeyEnc(generated.getPrivateKeyEnc())
                    .addressIndex(index)
                    .status(AddressStatus.AVAILABLE.getCode())
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
            userAddressMapper.insert(address);
        }
        log.info("Pre-generated {} addresses starting from index {}", count, startIndex);
    }

    public Optional<UserAddress> findByAddress(String address) {
        String normalized = AddressUtil.normalize(address);
        UserAddress result = userAddressMapper.selectOne(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getAddress, normalized));
        return Optional.ofNullable(result);
    }

    private UserAddress pickAvailableAddress() {
        return userAddressMapper.selectOne(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getStatus, AddressStatus.AVAILABLE.getCode())
                        .last("LIMIT 1"));
    }
}
