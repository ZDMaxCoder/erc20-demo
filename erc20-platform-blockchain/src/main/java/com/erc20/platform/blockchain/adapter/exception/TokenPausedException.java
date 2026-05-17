package com.erc20.platform.blockchain.adapter.exception;

public final class TokenPausedException extends ERC20AdapterException {

    private static final long serialVersionUID = 1L;

    private final String contractAddress;

    public TokenPausedException(String contractAddress) {
        super("Token contract is paused: " + contractAddress.toLowerCase());
        this.contractAddress = contractAddress.toLowerCase();
    }

    public String getContractAddress() {
        return contractAddress;
    }

    @Override
    public String toString() {
        return "TokenPausedException{contractAddress='" + contractAddress + "'}";
    }
}
