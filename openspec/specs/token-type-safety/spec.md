## ADDED Requirements

### Requirement: TokenConfig includes tokenType classification

`TokenConfig` entity SHALL include a `tokenType` field (String) with values: STANDARD, FEE_ON_TRANSFER, REBASING, UNSUPPORTED. Default is STANDARD. A V9 database migration adds this column.

#### Scenario: Existing tokens default to STANDARD
- **WHEN** the V9 migration runs on an existing database
- **THEN** all existing tokens have tokenType = 'STANDARD'

#### Scenario: New token with explicit type
- **WHEN** a new token is configured with tokenType = 'FEE_ON_TRANSFER'
- **THEN** the token record stores this type

### Requirement: DepositService rejects non-STANDARD token deposits

`DepositService.onTransferEvent()` SHALL check the token's `tokenType`. If not STANDARD, the event SHALL be logged and skipped (no deposit record created).

#### Scenario: Transfer event for STANDARD token
- **WHEN** a Transfer event arrives for a token with tokenType=STANDARD
- **THEN** normal deposit processing proceeds

#### Scenario: Transfer event for FEE_ON_TRANSFER token
- **WHEN** a Transfer event arrives for a token with tokenType=FEE_ON_TRANSFER
- **THEN** the event is logged as "unsupported token type" and no deposit record is created

### Requirement: DepositService filters mint events

`DepositService.onTransferEvent()` SHALL skip Transfer events where the `from` address is the zero address (0x0000...0000), as these are mint events, not user deposits.

#### Scenario: Normal user transfer
- **WHEN** a Transfer event has from=0xabc... (non-zero) and to=user deposit address
- **THEN** deposit processing proceeds normally

#### Scenario: Mint event (from zero address)
- **WHEN** a Transfer event has from=0x0000000000000000000000000000000000000000
- **THEN** the event is logged as "mint event skipped" and no deposit record is created

### Requirement: CollectionService skips non-STANDARD tokens

`CollectionService.scanForCollection()` SHALL only process tokens with tokenType=STANDARD.

#### Scenario: Collection scan for STANDARD token
- **WHEN** scanForCollection is called with a token having tokenType=STANDARD
- **THEN** addresses are scanned and collection tasks created normally

#### Scenario: Collection scan for unsupported token
- **WHEN** scanForCollection is called with a token having tokenType=FEE_ON_TRANSFER
- **THEN** the method returns immediately without scanning any addresses
