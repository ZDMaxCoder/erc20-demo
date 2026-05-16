## Why

Production security audit revealed 20+ vulnerabilities across the platform's fund handling, ERC-20 compatibility, and operational safety. Critical issues include: ReorgHandler never reverses credited balances (H8), TransactionConfirmTracker doesn't verify Transfer events (H6), AmountUtil allows long overflow (H4), and RiskControlService is implemented but never called (H9). Without fixes, the platform risks fund loss during chain reorgs, silent transfer failures, and uncontrolled withdrawals.

## What Changes

- Fix ReorgHandler to reverse credited deposits and revert confirmed withdrawals during reorg (H5, H8)
- Add Transfer event verification to TransactionConfirmTracker (H6)
- Add overflow protection to AmountUtil and callers (H4)
- Integrate RiskControlService into withdrawal flow (H9)
- Add withdrawal confirmation amount verification (H1)
- Add SIGNING timeout handling and stuck tx acceleration (W3, W5)
- Add eth_call pre-check before ERC-20 transfers (W4)
- Filter mint events and reject unsupported token types in DepositService (H2, D6)
- Add overflow protection and arrival verification to CollectionService (H3, C2, C4)
- Add chain-based balance reconciliation (H7)
- Harden SafeERC20Caller: empty return guard, retry, configurable from-address, bytes32 parsing (E2-E7)
- Fix MqCompensationJob to verify confirmations before crediting (H10)
- Enhance AlertService with fine-grained dedup keys (A2)
- Add token admin event monitoring (A4)
- Fix TransactionBuilder hardcoded chainId (L1)
- Add pagination to DepositConfirmJob (L2)
- Fix ERC20TransferEventParser data alignment (L3)

## Capabilities

### New Capabilities

- `reorg-fund-reversal`: Reorg detection reverses credited deposits and confirmed withdrawals, ensuring accounting consistency during chain reorganizations
- `transfer-event-verification`: Transaction confirmation validates actual Transfer event presence and amount, catching silent failures and fee-on-transfer discrepancies
- `amount-overflow-protection`: System-wide overflow guards prevent long-capacity breaches from corrupting balances
- `withdraw-risk-integration`: Risk control rules evaluated on every withdrawal creation, auto-pass/reject/manual-review
- `chain-reconciliation`: Daily on-chain balance verification against platform accounting for all active tokens and wallets
- `erc20-caller-hardening`: SafeERC20Caller resilience improvements (retry, empty-guard, configurable caller, bytes32 fix)
- `token-type-safety`: Token classification (STANDARD/UNSUPPORTED) rejecting fee-on-transfer and rebasing tokens at intake
- `admin-event-monitoring`: Monitor token contract admin events (Pause, Blacklist, Upgrade) for operational safety

### Modified Capabilities

## Impact

- erc20-platform-blockchain: ReorgHandler, TransactionConfirmTracker, GasEstimator, TransactionBuilder, SafeERC20Caller, ERC20TransferEventParser, new AdminEventMonitor
- erc20-platform-service: DepositService, WithdrawService, WithdrawRetryJob, CollectionService, AccountReconcileJob, AlertService, new ChainReconcileJob
- erc20-platform-mq: MqCompensationJob, TxStatusConsumer
- erc20-platform-common: AmountUtil, ErrorCode, DepositStatus enum
- erc20-platform-domain: TokenConfig entity
- erc20-platform-dal: New V9 migration (token_type column)
- All modules: New unit tests for each fix
