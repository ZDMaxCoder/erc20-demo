package com.erc20.platform.blockchain.erc20;

public class ChainCallException extends RuntimeException {

    private final String contract;

    public ChainCallException(String contract, Throwable cause) {
        super("Chain call failed for contract " + contract, cause);
        this.contract = contract;
    }

    public ChainCallException(String contract, String message) {
        super("Chain call failed for contract " + contract + ": " + message);
        this.contract = contract;
    }

    public String getContract() {
        return contract;
    }
}
