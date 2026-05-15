package com.erc20.platform.blockchain.wallet;

import org.web3j.crypto.RawTransaction;

public interface TransactionSigner {

    String sign(RawTransaction rawTx, int chainId);
}
