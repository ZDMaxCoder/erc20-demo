package com.erc20.platform.service.gateway;

public interface WithdrawMessagePublisher {

    void sendExecuteMessage(long withdrawId);
}
