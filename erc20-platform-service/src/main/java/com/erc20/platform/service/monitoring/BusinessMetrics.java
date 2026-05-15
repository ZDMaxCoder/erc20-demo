package com.erc20.platform.service.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BusinessMetrics {

    private final Counter depositCount;
    private final Counter withdrawCount;
    private final Counter collectionCount;
    private final Counter blockSyncedCount;
    private final Counter reorgCount;

    private final Timer depositConfirmDuration;
    private final Timer withdrawProcessDuration;

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.depositCount = Counter.builder("deposit.count")
                .description("Total deposits credited")
                .register(registry);

        this.withdrawCount = Counter.builder("withdraw.count")
                .description("Total withdrawals completed")
                .register(registry);

        this.collectionCount = Counter.builder("collection.count")
                .description("Total collection tasks completed")
                .register(registry);

        this.blockSyncedCount = Counter.builder("block.synced.count")
                .description("Total blocks synced")
                .register(registry);

        this.reorgCount = Counter.builder("reorg.count")
                .description("Total reorgs detected")
                .register(registry);

        this.depositConfirmDuration = Timer.builder("deposit.confirm.duration")
                .description("Time to confirm a deposit")
                .register(registry);

        this.withdrawProcessDuration = Timer.builder("withdraw.process.duration")
                .description("Time to process a withdrawal")
                .register(registry);
    }

    public void incrementDeposit() {
        depositCount.increment();
    }

    public void incrementWithdraw() {
        withdrawCount.increment();
    }

    public void incrementCollection() {
        collectionCount.increment();
    }

    public void incrementBlockSynced() {
        blockSyncedCount.increment();
    }

    public void incrementReorg() {
        reorgCount.increment();
    }

    public void recordDepositConfirmDuration(long millis) {
        depositConfirmDuration.record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordWithdrawProcessDuration(long millis) {
        withdrawProcessDuration.record(millis, TimeUnit.MILLISECONDS);
    }
}
