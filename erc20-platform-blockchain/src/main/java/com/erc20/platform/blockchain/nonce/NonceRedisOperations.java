package com.erc20.platform.blockchain.nonce;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class NonceRedisOperations {

    private static final String PENDING_KEY = "nonce:pending:%d:%s";
    private static final String GAPS_KEY = "nonce:gaps:%d:%s";
    private static final String ALLOCATED_KEY = "nonce:allocated:%d:%s";

    private final RedissonClient redissonClient;

    public NonceRedisOperations(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public Long getPendingNonce(int chainId, String address) {
        RBucket<Long> bucket = redissonClient.getBucket(String.format(PENDING_KEY, chainId, address));
        return bucket.get();
    }

    public void setPendingNonce(int chainId, String address, long nonce) {
        RBucket<Long> bucket = redissonClient.getBucket(String.format(PENDING_KEY, chainId, address));
        bucket.set(nonce);
    }

    public Long popSmallestGap(int chainId, String address) {
        RSortedSet<Long> gaps = redissonClient.getSortedSet(String.format(GAPS_KEY, chainId, address));
        if (gaps.isEmpty()) {
            return null;
        }
        Long smallest = gaps.first();
        gaps.remove(smallest);
        return smallest;
    }

    public void addToGaps(int chainId, String address, long nonce) {
        RSortedSet<Long> gaps = redissonClient.getSortedSet(String.format(GAPS_KEY, chainId, address));
        gaps.add(nonce);
    }

    public void addToAllocated(int chainId, String address, long nonce) {
        RScoredSortedSet<Long> allocated = redissonClient.getScoredSortedSet(
                String.format(ALLOCATED_KEY, chainId, address));
        allocated.add(System.currentTimeMillis(), nonce);
    }

    public void removeFromAllocated(int chainId, String address, long nonce) {
        RScoredSortedSet<Long> allocated = redissonClient.getScoredSortedSet(
                String.format(ALLOCATED_KEY, chainId, address));
        allocated.remove(nonce);
    }

    public Set<Long> getTimedOutAllocations(int chainId, String address, long cutoffTimestamp) {
        RScoredSortedSet<Long> allocated = redissonClient.getScoredSortedSet(
                String.format(ALLOCATED_KEY, chainId, address));
        Collection<Long> timedOut = allocated.valueRange(0, true, cutoffTimestamp, true);
        return new HashSet<Long>(timedOut);
    }

    public void clearAll(int chainId, String address) {
        redissonClient.getBucket(String.format(PENDING_KEY, chainId, address)).delete();
        redissonClient.getSortedSet(String.format(GAPS_KEY, chainId, address)).delete();
        redissonClient.getScoredSortedSet(String.format(ALLOCATED_KEY, chainId, address)).delete();
    }

    public List<String> getAllAllocatedKeys() {
        RKeys keys = redissonClient.getKeys();
        List<String> result = new ArrayList<String>();
        for (String key : keys.getKeysByPattern("nonce:allocated:*")) {
            String suffix = key.substring("nonce:allocated:".length());
            result.add(suffix);
        }
        return result;
    }
}
