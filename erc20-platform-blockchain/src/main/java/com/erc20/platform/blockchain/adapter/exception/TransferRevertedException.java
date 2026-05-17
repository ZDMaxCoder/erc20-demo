package com.erc20.platform.blockchain.adapter.exception;

public final class TransferRevertedException extends ERC20AdapterException {

    private static final long serialVersionUID = 1L;

    private final String contractAddress;
    private final String revertReason;

    public TransferRevertedException(String contractAddress, String revertReason) {
        super("Transfer reverted on contract " + contractAddress.toLowerCase() + ": " + revertReason);
        this.contractAddress = contractAddress.toLowerCase();
        this.revertReason = revertReason;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public String getRevertReason() {
        return revertReason;
    }

    @Override
    public String toString() {
        return "TransferRevertedException{contractAddress='" + contractAddress
                + "', revertReason='" + revertReason + "'}";
    }
}
