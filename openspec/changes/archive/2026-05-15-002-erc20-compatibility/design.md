## Context

ERC-20 is a standard interface, but many deployed tokens have non-standard implementations:
1. **USDT (Tether)**: transfer/approve functions have no return value (void instead of bool)
2. **BNB**: approve requires setting allowance to 0 first before new value
3. **Some tokens**: Transfer event has different indexed parameter count
4. **Some tokens**: decimals() returns bytes32 instead of uint8
5. **Some tokens**: name()/symbol() return bytes32 instead of string
6. **Some tokens**: Fee-on-transfer (actual received amount < sent amount)

## Goals / Non-Goals

**Goals:**
- Handle all known non-standard ERC-20 patterns gracefully
- Parse Transfer events from both standard and non-standard tokens
- Provide reliable token metadata reading
- Configure Web3j with production-grade resilience

**Non-Goals:**
- Not implementing fee-on-transfer detection in this change (will be handled in deposit service)
- Not implementing proxy contract detection

## Decisions

### SafeERC20Caller approach
Check receipt.status first. If function has return data, decode and verify bool. If no return data but status=1, treat as success. This matches OpenZeppelin's SafeERC20 library pattern.
**Why:** Battle-tested approach used in Solidity itself. Handles all known non-standard tokens.

### Transfer event parsing strategy
1. Verify topics[0] == keccak256("Transfer(address,address,uint256)")
2. Standard: topics.length==3, from=topics[1], to=topics[2], value=data
3. Non-standard: handle topics.length==2 or data containing multiple values
4. Return Optional.empty() on parse failure instead of throwing
**Why:** Defensive parsing ensures one malformed log doesn't crash the entire block processing.

### Web3j failover
Primary + backup RPC URL. On connection failure or timeout, switch to backup. Exponential backoff before retrying primary.
**Why:** Single node failure shouldn't halt the platform.

## Risks / Trade-offs

- [Risk] Unknown non-standard implementations → Mitigation: Optional.empty() return + WARN log, manual investigation for new tokens
- [Risk] Web3j connection pool exhaustion under load → Mitigation: configurable pool size, connection timeout

## Implementation Context

**Depends on from 001-project-foundation:**
- Web3j 4.9.8 dependency declared
- SLF4J logging configured
- Spring Boot component scanning

**Key outputs consumed by later changes:**
```java
// SafeERC20Caller
CompletableFuture<TransactionReceipt> safeTransfer(String contract, String to, BigInteger amount);
CompletableFuture<TransactionReceipt> safeApprove(String contract, String spender, BigInteger amount);
BigInteger safeBalanceOf(String contract, String owner);
int safeDecimals(String contract);
String safeSymbol(String contract);

// ERC20TransferEventParser
Optional<TransferEvent> parse(Log log, String contractAddress);
List<TransferEvent> parseFromReceipt(TransactionReceipt receipt, String contractAddress);

// TransferEvent (value object)
String contractAddress, from, to; BigInteger value; String txHash; long blockNumber; int logIndex;

// TRANSFER_EVENT_SIGNATURE = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
```
