package com.erc20.platform.mq;

public final class MqConstants {

    private MqConstants() {
    }

    // --- Topics ---
    public static final String TOPIC_BLOCK_TRANSFER_EVENT = "BLOCK_TRANSFER_EVENT";
    public static final String TOPIC_WITHDRAW_EXECUTE = "WITHDRAW_EXECUTE";
    public static final String TOPIC_DEPOSIT_CONFIRMED = "DEPOSIT_CONFIRMED";
    public static final String TOPIC_TX_STATUS_CHANGED = "TX_STATUS_CHANGED";
    public static final String TOPIC_PLATFORM_ALERT = "PLATFORM_ALERT";
    public static final String TOPIC_COLLECTION_TASK = "COLLECTION_TASK";

    // --- Tags ---
    public static final String TAG_DEPOSIT = "DEPOSIT";
    public static final String TAG_APPROVED = "APPROVED";
    public static final String TAG_CONFIRMED = "CONFIRMED";
    public static final String TAG_FAILED = "FAILED";

    // --- Consumer Groups ---
    public static final String GROUP_DEPOSIT_EVENT = "deposit-event-consumer-group";
    public static final String GROUP_WITHDRAW_EXECUTE = "withdraw-execute-consumer-group";
    public static final String GROUP_DEPOSIT_CONFIRMED = "deposit-confirmed-consumer-group";
    public static final String GROUP_TX_STATUS_WITHDRAW = "withdraw-tx-status-consumer-group";
    public static final String GROUP_TX_STATUS_COLLECTION = "collection-tx-status-consumer-group";
    public static final String GROUP_PLATFORM_ALERT = "platform-alert-consumer-group";
    public static final String GROUP_COLLECTION_TASK = "collection-task-consumer-group";

    // --- Redis idempotency key prefix ---
    public static final String REDIS_CONSUMED_PREFIX = "mq:consumed:";
    public static final long REDIS_CONSUMED_TTL_HOURS = 24;
}
