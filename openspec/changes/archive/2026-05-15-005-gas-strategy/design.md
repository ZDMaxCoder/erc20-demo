## Context

EIP-1559 introduced a new fee mechanism: baseFee (burned, set by protocol) + priorityFee (tip to miner). Legacy mode uses a single gasPrice. The platform must support both modes and automatically select based on network support.

Transaction replacement requires the new transaction to have the same nonce but at least 10% higher gas price (enforced by most nodes).

## Goals / Non-Goals

**Goals:**
- Support 4 priority levels: LOW, MEDIUM, HIGH, URGENT
- Provide safe replacement pricing (guaranteed ≥10% increase)
- Cap maximum gas price to prevent cost blowups
- Cache gas prices to reduce RPC overhead
- Detect and handle stuck transactions

**Non-Goals:**
- Not implementing MEV-related strategies
- Not implementing gas token optimization

## Decisions

### EIP-1559 pricing based on eth_feeHistory
Use recent block history to calculate percentile-based priority fees. Apply multipliers to baseFee for each priority level.
**Why:** More accurate than flat multipliers. Adapts to actual network conditions.

### Max gas price cap
Hard cap at configurable value (default 100 Gwei). If current price exceeds cap, pause outgoing transactions and alert.
**Why:** Prevents massive loss during gas spikes (e.g., during NFT mints or network congestion).

### 15-second cache refresh
Gas prices cached in Redis, refreshed every 15 seconds. Stale value used if RPC unavailable.
**Why:** Balances freshness with RPC call reduction. 15s is well within a single block time (~12s).

### Replacement: max(original * 1.15, original + 1 Gwei)
**Why:** Ethereum nodes typically require at least 10% increase. Using 15% provides safety margin.

## Risks / Trade-offs

- [Risk] Gas price cap pauses withdrawals during congestion → Mitigation: alert operator, provide manual override via admin API
- [Risk] Stale cached price leads to underpriced transactions → Mitigation: 15s TTL is short, stuck handler will accelerate if needed

## Implementation Context

**Depends on:**
```java
// Web3j calls
web3j.ethGasPrice()
web3j.ethFeeHistory(blockCount, "latest", rewardPercentiles)
web3j.ethEstimateGas(Transaction)

// From 004 - NonceManager (for stuck tx replacement)
NonceManager.allocateNonce / releaseNonce
```

**Key outputs:**
```java
public interface GasStrategy {
    GasPrice getGasPrice(GasPriority priority);
    GasPrice getReplacementGasPrice(GasPrice original);
}

// GasPrice: gasPrice (legacy), maxFeePerGas + maxPriorityFeePerGas (EIP-1559), boolean eip1559
// GasPriority: LOW, MEDIUM, HIGH, URGENT

BigInteger estimateERC20Transfer(String contract, String from, String to, BigInteger amount);
```

**Configuration:**
```yaml
blockchain.gas:
  strategy: eip1559
  max-gas-price: 100000000000  # 100 Gwei
  stuck-timeout-minutes: 5
  max-replacement-count: 3
  gas-limit-buffer-percent: 20
```
