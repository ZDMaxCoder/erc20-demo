package com.erc20.platform.blockchain.adapter.exception;

import com.erc20.platform.blockchain.adapter.model.CallResult;

public final class TransferPreCheckFailedException extends ERC20AdapterException {

    private static final long serialVersionUID = 1L;

    private final String contractAddress;
    private final CallResult callResult;

    public TransferPreCheckFailedException(String contractAddress, CallResult callResult) {
        super("Transfer pre-check failed for contract " + contractAddress.toLowerCase()
                + ": " + callResult.getOutcome());
        this.contractAddress = contractAddress.toLowerCase();
        this.callResult = callResult;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public CallResult getCallResult() {
        return callResult;
    }

    @Override
    public String toString() {
        return "TransferPreCheckFailedException{contractAddress='" + contractAddress
                + "', callResult=" + callResult + "}";
    }
}
