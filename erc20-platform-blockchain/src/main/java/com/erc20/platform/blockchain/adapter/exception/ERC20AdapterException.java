package com.erc20.platform.blockchain.adapter.exception;

public class ERC20AdapterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ERC20AdapterException(String message) {
        super(message);
    }

    public ERC20AdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
