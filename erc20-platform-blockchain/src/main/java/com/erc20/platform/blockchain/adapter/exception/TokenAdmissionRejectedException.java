package com.erc20.platform.blockchain.adapter.exception;

public final class TokenAdmissionRejectedException extends ERC20AdapterException {

    private static final long serialVersionUID = 1L;

    private final String contractAddress;
    private final String reason;

    public TokenAdmissionRejectedException(String contractAddress, String reason) {
        super("Token " + contractAddress.toLowerCase() + " admission rejected: " + reason);
        this.contractAddress = contractAddress.toLowerCase();
        this.reason = reason;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "TokenAdmissionRejectedException{contractAddress='" + contractAddress
                + "', reason='" + reason + "'}";
    }
}
