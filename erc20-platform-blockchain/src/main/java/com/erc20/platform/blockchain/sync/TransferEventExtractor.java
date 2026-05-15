package com.erc20.platform.blockchain.sync;

import com.erc20.platform.blockchain.erc20.TransferEvent;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.io.IOException;
import java.util.List;

public interface TransferEventExtractor {

    List<TransferEvent> extractFromBlock(long blockNumber, EthBlock.Block block) throws IOException;
}
