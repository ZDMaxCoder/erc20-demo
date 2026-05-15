package com.erc20.platform.blockchain.nonce;

import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NonceGapDetector {

    private static final long TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private final NonceRedisOperations redisOps;
    private final AlertService alertService;

    public NonceGapDetector(NonceRedisOperations redisOps, AlertService alertService) {
        this.redisOps = redisOps;
        this.alertService = alertService;
    }

    @Scheduled(fixedDelay = 30000)
    public void detectAndReclaimTimedOutNonces() {
        long cutoff = System.currentTimeMillis() - TIMEOUT_MILLIS;
        List<String> keys = redisOps.getAllAllocatedKeys();
        for (String key : keys) {
            String[] parts = key.split(":", 2);
            int chainId = Integer.parseInt(parts[0]);
            String address = parts[1];

            Set<Long> timedOut = redisOps.getTimedOutAllocations(chainId, address, cutoff);
            if (!timedOut.isEmpty()) {
                alertService.alert("NONCE_GAP", AlertLevel.WARN,
                        "Nonce gaps detected for chain=" + chainId + " address=" + address
                                + " count=" + timedOut.size());
            }
            for (Long nonce : timedOut) {
                log.warn("Reclaiming timed-out nonce {} for chain={} address={}", nonce, chainId, address);
                redisOps.addToGaps(chainId, address, nonce);
                redisOps.removeFromAllocated(chainId, address, nonce);
            }
        }
    }
}
