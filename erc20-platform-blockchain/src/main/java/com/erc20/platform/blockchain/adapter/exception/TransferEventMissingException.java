package com.erc20.platform.blockchain.adapter.exception;

public final class TransferEventMissingException extends ERC20AdapterException {

    private static final long serialVersionUID = 1L;

    private final String txHash;

    public TransferEventMissingException(String txHash) {
        super("Transfer event missing in transaction " + txHash);
        this.txHash = txHash;
    }

    public String getTxHash() {
        return txHash;
    }

    @Override
    public String toString() {
        return "TransferEventMissingException{txHash='" + txHash + "'}";
    }
}
