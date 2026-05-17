## ADDED Requirements

### Requirement: Decode standard bool return value

`ReturnValueDecoder` SHALL correctly decode a standard ERC-20 bool return value (32 bytes, value 0x01) as `CallOutcome.SUCCESS`.

#### Scenario: Standard true return (0x01 padded to 32 bytes)

- **WHEN** the hex result is `"0x0000000000000000000000000000000000000000000000000000000000000001"`
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `SUCCESS`

#### Scenario: Standard true return without 0x prefix

- **WHEN** the hex result is `"0000000000000000000000000000000000000000000000000000000000000001"`
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `SUCCESS`

### Requirement: Decode void return (no return value)

`ReturnValueDecoder` SHALL treat an empty or null response as `CallOutcome.SUCCESS_NO_RETURN`, compatible with USDT-like tokens that do not return a bool.

#### Scenario: Null response

- **WHEN** the hex result is `null`
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `SUCCESS_NO_RETURN`

#### Scenario: Empty hex "0x"

- **WHEN** the hex result is `"0x"`
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `SUCCESS_NO_RETURN`

#### Scenario: Empty string

- **WHEN** the hex result is `""`
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `SUCCESS_NO_RETURN`

### Requirement: Decode false return without revert

`ReturnValueDecoder` SHALL decode a return value of 0x00 (32 bytes zero) as `CallOutcome.RETURNED_FALSE`, indicating the contract returned `false` without reverting. This is a dangerous condition.

#### Scenario: Returns false (all zeros, 32 bytes)

- **WHEN** the hex result is `"0x0000000000000000000000000000000000000000000000000000000000000000"`
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `RETURNED_FALSE`

### Requirement: Handle incomplete return data

`ReturnValueDecoder` SHALL treat return data shorter than 64 hex chars (32 bytes) as `SUCCESS_NO_RETURN`, as some contracts may return incomplete data.

#### Scenario: Short return data (less than 32 bytes)

- **WHEN** the hex result is `"0x01"` (only 1 byte)
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `SUCCESS_NO_RETURN`

#### Scenario: Return data exactly 2 chars after 0x prefix

- **WHEN** the hex result is `"0xff"`
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `SUCCESS_NO_RETURN`

### Requirement: Handle non-standard large values

`ReturnValueDecoder` SHALL treat any non-zero value other than 1 as `CallOutcome.UNKNOWN`, preserving the raw hex for inspection.

#### Scenario: Non-standard non-zero value (e.g., 0x02)

- **WHEN** the hex result is `"0x0000000000000000000000000000000000000000000000000000000000000002"`
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `UNKNOWN` and preserve the raw hex value

#### Scenario: Large non-standard value

- **WHEN** the hex result is `"0x000000000000000000000000000000000000000000000000000000000000ffff"`
- **THEN** `decodeBoolReturn()` SHALL return a `CallResult` with outcome `UNKNOWN` and preserve the raw hex value

### Requirement: CallResult provides semantic query methods

`CallResult` SHALL provide convenience methods for callers to determine the semantic meaning of the result.

#### Scenario: isSuccess covers both SUCCESS and SUCCESS_NO_RETURN

- **WHEN** a `CallResult` has outcome `SUCCESS` or `SUCCESS_NO_RETURN`
- **THEN** `isSuccess()` SHALL return `true`

#### Scenario: isDangerousFalse identifies false-no-revert

- **WHEN** a `CallResult` has outcome `RETURNED_FALSE`
- **THEN** `isDangerousFalse()` SHALL return `true`
- **THEN** `isSuccess()` SHALL return `false`

#### Scenario: isSuccess returns false for UNKNOWN

- **WHEN** a `CallResult` has outcome `UNKNOWN`
- **THEN** `isSuccess()` SHALL return `false`
- **THEN** `isDangerousFalse()` SHALL return `false`
