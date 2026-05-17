## ADDED Requirements

### Requirement: ConsecutiveFailureBreaker records transfer outcomes

The system SHALL maintain a per-token consecutive failure counter. When `TransactionConfirmTracker` confirms a transaction as SUCCESS, the counter for that token MUST be reset to zero. When confirmed as FAILED, the counter MUST be atomically incremented.

#### Scenario: Successful transfer resets counter
- **WHEN** a transaction for token 0xabc is confirmed as SUCCESS
- **THEN** the consecutive failure counter for 0xabc is reset to zero

#### Scenario: Failed transfer increments counter
- **WHEN** a transaction for token 0xabc is confirmed as FAILED
- **THEN** the consecutive failure counter for 0xabc is atomically incremented by 1

#### Scenario: ANOMALY outcome does not affect counter
- **WHEN** a transaction for token 0xabc is confirmed as ANOMALY
- **THEN** the consecutive failure counter for 0xabc remains unchanged

### Requirement: Automatic circuit breaker trip on threshold

When the consecutive failure counter for a token reaches the configured threshold (default 5), the system SHALL automatically trip the circuit breaker for that token: set `circuit_breaker_status` to `OPEN` in the database, invalidate the `TokenRiskProfileRegistry` cache, and fire a `TOKEN_CIRCUIT_BREAKER_TRIPPED` HIGH alert.

#### Scenario: Counter reaches threshold
- **WHEN** the failure counter for token 0xabc reaches 5 (configured threshold)
- **THEN** `t_token_config.circuit_breaker_status` is set to `OPEN` for that token
- **AND** `TokenRiskProfileRegistry` cache is invalidated for 0xabc
- **AND** an alert with level HIGH and type `TOKEN_CIRCUIT_BREAKER_TRIPPED` is raised

#### Scenario: Counter below threshold
- **WHEN** the failure counter for token 0xabc is at 3 (below threshold of 5)
- **THEN** no circuit breaker action is taken

### Requirement: Circuit breaker blocks new write operations

When a token's `circuit_breaker_status` is `OPEN`, the `TokenAdmissionGateway` SHALL reject write operations (transfer, approve) for that token by throwing `TokenAdmissionRejectedException` with reason indicating circuit breaker is open.

#### Scenario: Write operation rejected when breaker is OPEN
- **WHEN** `safeTransfer` is called for a token with `circuit_breaker_status=OPEN`
- **THEN** `TokenAdmissionRejectedException` is thrown with message containing "circuit breaker is OPEN"

#### Scenario: Read operations allowed when breaker is OPEN
- **WHEN** `balanceOf` is called for a token with `circuit_breaker_status=OPEN`
- **THEN** the call succeeds normally (read operations bypass admission gateway)

#### Scenario: Breaker CLOSED allows normal operations
- **WHEN** `safeTransfer` is called for a token with `circuit_breaker_status=CLOSED`
- **THEN** the operation proceeds through normal admission checks

### Requirement: Manual circuit breaker reset

The system SHALL provide a `resetBreaker(String contract)` method that sets `circuit_breaker_status` to `CLOSED`, clears the Redis failure counter, invalidates the registry cache, and logs the reset.

#### Scenario: Reset breaker restores normal operation
- **WHEN** `resetBreaker("0xabc")` is called while breaker is OPEN
- **THEN** `circuit_breaker_status` is set to `CLOSED`
- **AND** the Redis failure counter is deleted
- **AND** `TokenRiskProfileRegistry` cache is invalidated
- **AND** subsequent `safeTransfer` calls for 0xabc are no longer rejected by the breaker

#### Scenario: Reset breaker on already-CLOSED token is no-op
- **WHEN** `resetBreaker("0xabc")` is called while breaker is already CLOSED
- **THEN** no database update occurs and no alert is raised

### Requirement: Database migration for circuit breaker status

A V12 Flyway migration SHALL add `circuit_breaker_status VARCHAR(16) DEFAULT 'CLOSED'` to `t_token_config`.

#### Scenario: Migration adds column
- **WHEN** V12 migration runs
- **THEN** `t_token_config` has a `circuit_breaker_status` column with default value `'CLOSED'`
- **AND** all existing rows have `circuit_breaker_status = 'CLOSED'`

### Requirement: Circuit breaker status in TokenRiskProfile

`TokenRiskProfile` SHALL include a `circuitBreakerOpen` boolean field. `TokenRiskProfileRegistry` SHALL read `circuit_breaker_status` from the database and set this field to true when the value is `OPEN`.

#### Scenario: Profile reflects OPEN status
- **WHEN** `getProfile("0xabc")` is called and DB has `circuit_breaker_status='OPEN'`
- **THEN** the returned `TokenRiskProfile` has `circuitBreakerOpen=true`

#### Scenario: Profile reflects CLOSED status
- **WHEN** `getProfile("0xabc")` is called and DB has `circuit_breaker_status='CLOSED'`
- **THEN** the returned `TokenRiskProfile` has `circuitBreakerOpen=false`
