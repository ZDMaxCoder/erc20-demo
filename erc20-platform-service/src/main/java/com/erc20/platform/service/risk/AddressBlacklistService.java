package com.erc20.platform.service.risk;

import com.erc20.platform.dal.mapper.AddressBlacklistMapper;
import com.erc20.platform.domain.entity.AddressBlacklist;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Locale;

@Slf4j
@Service
public class AddressBlacklistService {

    private static final String BLACKLIST_KEY = "risk:address:blacklist";

    private final RedissonClient redissonClient;
    private final AddressBlacklistMapper blacklistMapper;

    public AddressBlacklistService(RedissonClient redissonClient,
                                   AddressBlacklistMapper blacklistMapper) {
        this.redissonClient = redissonClient;
        this.blacklistMapper = blacklistMapper;
    }

    public boolean isBlacklisted(String address) {
        String normalized = normalize(address);
        RSet<String> set = redissonClient.getSet(BLACKLIST_KEY);
        return set.contains(normalized);
    }

    public void addToBlacklist(String address, String reason, String operator) {
        String normalized = normalize(address);
        RSet<String> set = redissonClient.getSet(BLACKLIST_KEY);
        set.add(normalized);

        AddressBlacklist entity = AddressBlacklist.builder()
                .address(normalized)
                .reason(reason)
                .operator(operator)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        blacklistMapper.insert(entity);
        log.info("Address added to blacklist: {} by {}", normalized, operator);
    }

    public void removeFromBlacklist(String address) {
        String normalized = normalize(address);
        RSet<String> set = redissonClient.getSet(BLACKLIST_KEY);
        set.remove(normalized);

        blacklistMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AddressBlacklist>()
                        .eq(AddressBlacklist::getAddress, normalized));
        log.info("Address removed from blacklist: {}", normalized);
    }

    private String normalize(String address) {
        return address.toLowerCase(Locale.ROOT);
    }
}
