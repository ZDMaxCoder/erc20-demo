-- Withdraw record table
CREATE TABLE t_withdraw_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL COMMENT 'External withdrawal request ID',
    idempotent_key VARCHAR(256) NOT NULL COMMENT 'Idempotent key: WD_requestId',
    user_id VARCHAR(64) NOT NULL COMMENT 'Withdrawing user ID',
    token_id BIGINT NOT NULL COMMENT 'FK to t_token_config',
    to_address VARCHAR(64) NOT NULL COMMENT 'Destination address',
    amount BIGINT NOT NULL COMMENT 'Withdrawal amount in min-unit',
    amount_exponent INT NOT NULL COMMENT 'Exponent for min-unit conversion',
    fee_amount BIGINT NOT NULL DEFAULT 0 COMMENT 'Fee in min-unit',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/BROADCASTING/SUCCESS/FAILED/REJECTED',
    tx_hash VARCHAR(128) COMMENT 'On-chain transaction hash after broadcast',
    error_message TEXT COMMENT 'Error message if FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'Number of broadcast retries',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Idempotency: prevent duplicate withdrawal requests
    UNIQUE INDEX uk_idempotent_key (idempotent_key),
    -- Unique request ID
    UNIQUE INDEX uk_request_id (request_id),
    -- Query withdrawals by user
    INDEX idx_user_id (user_id),
    -- Query withdrawals by status for processing
    INDEX idx_status (status)
) COMMENT 'Withdrawal records';

-- Transaction record table
CREATE TABLE t_transaction_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_hash VARCHAR(128) NOT NULL COMMENT 'On-chain transaction hash',
    from_address VARCHAR(64) NOT NULL COMMENT 'Sender address',
    to_address VARCHAR(64) NOT NULL COMMENT 'Receiver address (contract or EOA)',
    token_id BIGINT NOT NULL COMMENT 'FK to t_token_config',
    amount BIGINT NOT NULL COMMENT 'Amount in min-unit',
    amount_exponent INT NOT NULL COMMENT 'Exponent for min-unit conversion',
    gas_price BIGINT COMMENT 'Gas price in wei',
    gas_limit BIGINT COMMENT 'Gas limit',
    gas_used BIGINT COMMENT 'Actual gas used',
    nonce BIGINT COMMENT 'Transaction nonce',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUBMITTED/CONFIRMED/FAILED',
    biz_type VARCHAR(32) NOT NULL COMMENT 'WITHDRAW/COLLECTION/GAS_SUPPLY',
    biz_id BIGINT COMMENT 'Associated business record ID',
    block_number BIGINT COMMENT 'Confirmed block number',
    error_message TEXT COMMENT 'Error message if FAILED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Lookup by tx hash
    UNIQUE INDEX uk_tx_hash (tx_hash),
    -- Query by business type and ID for status tracking
    INDEX idx_biz (biz_type, biz_id),
    -- Query by status for retry/monitoring
    INDEX idx_status (status)
) COMMENT 'On-chain transaction records';

-- Nonce record table
CREATE TABLE t_nonce_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    address VARCHAR(64) NOT NULL COMMENT 'Wallet address',
    chain_id INT NOT NULL DEFAULT 1 COMMENT 'Chain ID',
    current_nonce BIGINT NOT NULL DEFAULT 0 COMMENT 'Next available nonce',
    pending_count INT NOT NULL DEFAULT 0 COMMENT 'Number of pending transactions',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- One nonce tracker per address per chain
    UNIQUE INDEX uk_address_chain (address, chain_id)
) COMMENT 'Nonce management for transaction ordering';
