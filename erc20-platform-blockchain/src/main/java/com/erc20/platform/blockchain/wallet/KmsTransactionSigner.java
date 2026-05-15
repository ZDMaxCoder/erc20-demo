package com.erc20.platform.blockchain.wallet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.web3j.crypto.RawTransaction;

@Slf4j
@Component
@Profile("prod")
public class KmsTransactionSigner implements TransactionSigner {

    @Override
    public String sign(RawTransaction rawTx, int chainId) {
        throw new UnsupportedOperationException("KMS signing not yet implemented");
    }
}
