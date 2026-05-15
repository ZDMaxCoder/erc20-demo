package com.erc20.platform.blockchain.wallet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

@Slf4j
@Component
@Profile("dev")
public class LocalKeySigner implements TransactionSigner {

    private final Credentials credentials;

    public LocalKeySigner(@Value("${blockchain.wallet.private-key}") String privateKey) {
        this.credentials = Credentials.create(privateKey);
        log.info("LocalKeySigner initialized for address: {}", credentials.getAddress());
    }

    @Override
    public String sign(RawTransaction rawTx, int chainId) {
        byte[] signedMessage = TransactionEncoder.signMessage(rawTx, chainId, credentials);
        return Numeric.toHexString(signedMessage);
    }
}
