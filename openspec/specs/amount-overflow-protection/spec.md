## ADDED Requirements

### Requirement: AmountUtil rejects overflow with AmountOverflowException

`AmountUtil.fromChainAmount()` and `toChainAmount()` SHALL check if the result exceeds `Long.MAX_VALUE` (bitLength > 63) before conversion. If overflow would occur, the methods SHALL throw `AmountOverflowException` (a subclass of BizException).

#### Scenario: Normal amount converts successfully
- **WHEN** fromChainAmount is called with a chain amount that fits in a long (e.g., 1000000 with decimals=6, exponent=6)
- **THEN** the method returns the correct long value

#### Scenario: Overflow amount throws AmountOverflowException
- **WHEN** fromChainAmount is called with a chain amount exceeding Long.MAX_VALUE after conversion (e.g., BigInteger of 10^30 with decimals=18, exponent=6)
- **THEN** AmountOverflowException is thrown with error code AMOUNT_OVERFLOW

#### Scenario: toChainAmount overflow
- **WHEN** toChainAmount is called and the result BigInteger would exceed 63 bits
- **THEN** AmountOverflowException is thrown

### Requirement: DepositService handles AmountOverflowException gracefully

When `AmountUtil.fromChainAmount()` throws `AmountOverflowException` during deposit processing, the system SHALL create the deposit record with a special status and raise a CRITICAL alert, rather than silently discarding the event.

#### Scenario: Deposit with overflow amount
- **WHEN** a Transfer event has an amount that causes overflow in AmountUtil.fromChainAmount()
- **THEN** the deposit record is created with status AMOUNT_OVERFLOW (new DepositStatus value) and a CRITICAL alert "DEPOSIT_OVERFLOW" is raised

### Requirement: CollectionService guards against overflow

When `CollectionService.checkAndCreateTask()` converts an on-chain balance to a long for the collection task amount, it SHALL check for overflow before the conversion.

#### Scenario: Collection balance within long range
- **WHEN** a user address balance is 5000000000 (fits in long)
- **THEN** a collection task is created normally

#### Scenario: Collection balance exceeds long range
- **WHEN** a user address balance exceeds Long.MAX_VALUE
- **THEN** no collection task is created, a CRITICAL alert is raised, and the address is skipped
