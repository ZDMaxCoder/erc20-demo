package com.erc20.platform.service.monitoring;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.dal.mapper.NonceRecordMapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.NonceRecord;
import com.erc20.platform.domain.entity.WithdrawRecord;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Component
public class GaugeRegistrar {

    private final MeterRegistry registry;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final NonceRecordMapper nonceRecordMapper;

    public GaugeRegistrar(MeterRegistry registry,
                          WithdrawRecordMapper withdrawRecordMapper,
                          NonceRecordMapper nonceRecordMapper) {
        this.registry = registry;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.nonceRecordMapper = nonceRecordMapper;
    }

    @PostConstruct
    public void registerGauges() {
        registry.gauge("pending.withdraw.count", this, GaugeRegistrar::getPendingWithdrawCount);
        registry.gauge("pending.nonce.count", this, GaugeRegistrar::getPendingNonceCount);
    }

    private double getPendingWithdrawCount() {
        try {
            Long count = withdrawRecordMapper.selectCount(
                    new LambdaQueryWrapper<WithdrawRecord>()
                            .eq(WithdrawRecord::getStatus, WithdrawStatus.PENDING_REVIEW.getCode()));
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to query pending withdraw count", e);
            return 0;
        }
    }

    private double getPendingNonceCount() {
        try {
            List<NonceRecord> records = nonceRecordMapper.selectList(
                    new LambdaQueryWrapper<NonceRecord>()
                            .gt(NonceRecord::getPendingCount, 0));
            int total = 0;
            for (NonceRecord r : records) {
                total += r.getPendingCount();
            }
            return total;
        } catch (Exception e) {
            log.warn("Failed to query pending nonce count", e);
            return 0;
        }
    }
}
