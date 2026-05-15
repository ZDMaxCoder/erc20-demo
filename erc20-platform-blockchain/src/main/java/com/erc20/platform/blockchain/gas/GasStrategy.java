package com.erc20.platform.blockchain.gas;

public interface GasStrategy {

    GasPrice getGasPrice(GasPriority priority);

    GasPrice getReplacementGasPrice(GasPrice original);
}
