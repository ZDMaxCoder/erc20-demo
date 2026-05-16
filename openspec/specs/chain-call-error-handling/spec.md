## ADDED Requirements

### Requirement: SafeERC20Caller single-attempt with ChainCallException

SafeERC20Caller.ethCall() SHALL execute exactly one RPC call. On IOException, it SHALL throw ChainCallException immediately without retry. On response error (hasError()), it SHALL throw ChainCallException with the error message.

#### Scenario: Successful eth_call
- **WHEN** eth_call succeeds and response has no error
- **THEN** the response value is returned normally

#### Scenario: IOException on eth_call
- **WHEN** eth_call throws IOException
- **THEN** ChainCallException is thrown immediately (no retry, no sleep)

#### Scenario: Response error on eth_call
- **WHEN** eth_call response hasError() is true
- **THEN** ChainCallException is thrown with the error message

### Requirement: ChainReconcileJob handles ChainCallException

ChainReconcileJob SHALL catch ChainCallException per token/address pair, raise a WARN alert, and continue processing remaining pairs.

#### Scenario: balanceOf fails for one token
- **WHEN** safeBalanceOf throws ChainCallException for token A
- **THEN** a CHAIN_CALL_FAILED WARN alert is raised for token A, and reconciliation continues with token B

### Requirement: CollectionService handles ChainCallException

CollectionService.checkAndCreateTask() SHALL catch ChainCallException and skip the current address without failing the entire scan.

#### Scenario: getERC20Balance fails for one address
- **WHEN** the balance query throws ChainCallException for address X
- **THEN** address X is skipped with a log.error, and scanning continues with the next address

### Requirement: MqCompensationJob handles ChainCallException

MqCompensationJob SHALL catch ChainCallException during deposit compensation. On failure, it SHALL NOT credit the deposit, SHALL refresh updatedAt to prevent immediate re-trigger, and SHALL log a warning.

#### Scenario: Chain query fails during compensation
- **WHEN** querying a deposit's receipt throws ChainCallException
- **THEN** the deposit is NOT credited, updatedAt is refreshed, and processing continues with the next deposit
