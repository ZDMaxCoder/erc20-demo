package com.erc20.platform.blockchain.config;

import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.service.AlertService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Response;

import java.io.IOException;

@Slf4j
public class Web3jProvider {

    private final Web3j primary;
    private final Web3j backup;
    private volatile Web3j current;

    @Setter
    private AlertService alertService;

    public Web3jProvider(Web3j primary, Web3j backup) {
        this.primary = primary;
        this.backup = backup;
        this.current = primary;
    }

    public Web3j getWeb3j() {
        return current;
    }

    public <T extends Response<?>> T sendWithFailover(RequestFunction<T> requestFunction) throws IOException {
        try {
            return requestFunction.apply(current);
        } catch (IOException e) {
            if (backup != null && current == primary) {
                log.warn("Primary RPC failed, switching to backup: {}", e.getMessage());
                current = backup;
                if (alertService != null) {
                    alertService.alert("RPC_FAILOVER", AlertLevel.WARN,
                            "Primary RPC failed, switched to backup: " + e.getMessage());
                }
                return requestFunction.apply(backup);
            }
            throw e;
        }
    }

    public void resetToPrimary() {
        this.current = primary;
    }

    @FunctionalInterface
    public interface RequestFunction<T> {
        T apply(Web3j web3j) throws IOException;
    }
}
