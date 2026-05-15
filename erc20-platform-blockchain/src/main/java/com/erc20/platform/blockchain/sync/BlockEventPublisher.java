package com.erc20.platform.blockchain.sync;

import com.erc20.platform.blockchain.erc20.TransferEvent;

import java.util.List;

public interface BlockEventPublisher {

    void publish(List<TransferEvent> events);
}
