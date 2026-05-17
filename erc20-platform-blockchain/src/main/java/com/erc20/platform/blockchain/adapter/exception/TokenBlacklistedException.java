package com.erc20.platform.blockchain.adapter.exception;

public final class TokenBlacklistedException extends ERC20AdapterException {

    private static final long serialVersionUID = 1L;

    private final String contractAddress;
    private final String address;

    public TokenBlacklistedException(String contractAddress, String address) {
        super("Address " + address.toLowerCase() + " is blacklisted by token contract " + contractAddress.toLowerCase());
        this.contractAddress = contractAddress.toLowerCase();
        this.address = address.toLowerCase();
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return "TokenBlacklistedException{contractAddress='" + contractAddress
                + "', address='" + address + "'}";
    }
}
