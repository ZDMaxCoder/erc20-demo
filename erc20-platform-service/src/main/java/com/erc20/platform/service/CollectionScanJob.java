package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CollectionScanJob {

    private final TokenConfigMapper tokenConfigMapper;
    private final CollectionService collectionService;
    private final CollectionProperties properties;

    public CollectionScanJob(TokenConfigMapper tokenConfigMapper,
                             CollectionService collectionService,
                             CollectionProperties properties) {
        this.tokenConfigMapper = tokenConfigMapper;
        this.collectionService = collectionService;
        this.properties = properties;
    }

    @Scheduled(cron = "0 0 */4 * * ?")
    public void scheduledScan() {
        if (!properties.isEnabled()) {
            return;
        }

        log.info("Starting scheduled collection scan");
        List<TokenConfig> tokens = tokenConfigMapper.selectList(
                new LambdaQueryWrapper<TokenConfig>()
                        .eq(TokenConfig::getEnabled, 1)
                        .isNotNull(TokenConfig::getCollectionThreshold));

        for (TokenConfig token : tokens) {
            try {
                collectionService.scanForCollection(token);
            } catch (Exception e) {
                log.error("Failed to scan collection for token {}", token.getId(), e);
            }
        }

        collectionService.batchCollection();
        log.info("Scheduled collection scan completed");
    }
}
