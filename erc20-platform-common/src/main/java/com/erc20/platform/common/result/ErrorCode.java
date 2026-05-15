package com.erc20.platform.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "success"),
    SYSTEM_ERROR(10000, "System error"),
    PARAM_ERROR(10001, "Invalid parameter"),
    NOT_FOUND(10002, "Resource not found"),
    DUPLICATE_REQUEST(10003, "Duplicate request"),
    INSUFFICIENT_BALANCE(20001, "Insufficient balance"),
    AMOUNT_TOO_SMALL(20002, "Amount below minimum"),
    TOKEN_DISABLED(20003, "Token is disabled"),
    ADDRESS_INVALID(20004, "Invalid address"),
    WITHDRAW_REJECTED(20005, "Withdrawal rejected"),
    ILLEGAL_STATE_TRANSITION(20006, "Illegal state transition"),
    LOCK_ACQUIRE_FAILED(30001, "Failed to acquire lock"),
    CHAIN_ERROR(40001, "Blockchain interaction error"),
    NONCE_CONFLICT(40002, "Nonce conflict"),
    BROADCAST_FAILED(40003, "Transaction broadcast failed"),
    INSUFFICIENT_FUNDS(40004, "Insufficient funds for transaction");

    private final int code;
    private final String message;
}
