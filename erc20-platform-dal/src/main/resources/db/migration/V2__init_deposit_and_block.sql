-- Deposit record table
CREATE TABLE t_deposit_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_hash VARCHAR(128) NOT NULL COMMENT 'On-chain transaction hash',
    log_index INT NOT NULL COMMENT 'Event log index within the transaction',
    idempotent_key VARCHAR(256) NOT NULL COMMENT 'Idempotent key: txHash_logIndex',
    user_id VARCHAR(64) NOT NULL COMMENT 'Depositing user ID',
    token_id BIGINT NOT NULL COMMENT 'FK to t_token_config',
    from_address VARCHAR(64) NOT NULL COMMENT 'Source address',
    to_address VARCHAR(64) NOT NULL COMMENT 'Deposit address (our user address)',
    amount BIGINT NOT NULL COMMENT 'Deposit amount in min-unit',
    amount_exponent INT NOT NULL COMMENT 'Exponent for min-unit conversion',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMING/SUCCESS/FAILED',
    block_number BIGINT NOT NULL COMMENT 'Block number of the deposit tx',
    block_hash VARCHAR(128) NOT NULL COMMENT 'Block hash',
    confirmations INT NOT NULL DEFAULT 0 COMMENT 'Current confirmation count',
    required_confirmations INT NOT NULL DEFAULT 12 COMMENT 'Required confirmations',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Idempotency: prevent duplicate deposit records for same on-chain event
    UNIQUE INDEX uk_idempotent_key (idempotent_key),
    -- Query deposits by user
    INDEX idx_user_id (user_id),
    -- Query deposits by status for confirmation processing
    INDEX idx_status (status),
    -- Query deposits by block for reorg handling
    INDEX idx_block_number (block_number)
) COMMENT 'Deposit records';

-- Block sync progress table
CREATE TABLE t_block_sync_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chain_id INT NOT NULL COMMENT 'Chain ID',
    last_synced_block BIGINT NOT NULL DEFAULT 0 COMMENT 'Last successfully synced block number',
    last_synced_block_hash VARCHAR(128) COMMENT 'Hash of last synced block for reorg detection',
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/PAUSED/ERROR',
    error_message TEXT COMMENT 'Last error message if status=ERROR',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- One progress record per chain
    UNIQUE INDEX uk_chain_id (chain_id)
) COMMENT 'Block synchronization progress tracker';

-- Block record table
CREATE TABLE t_block_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chain_id INT NOT NULL COMMENT 'Chain ID',
    block_number BIGINT NOT NULL COMMENT 'Block number',
    block_hash VARCHAR(128) NOT NULL COMMENT 'Block hash',
    parent_hash VARCHAR(128) NOT NULL COMMENT 'Parent block hash for reorg detection',
    block_timestamp DATETIME NOT NULL COMMENT 'Block timestamp',
    tx_count INT NOT NULL DEFAULT 0 COMMENT 'Number of transactions in block',
    synced TINYINT NOT NULL DEFAULT 1 COMMENT '1=synced, 0=pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Unique block per chain
    UNIQUE INDEX uk_chain_block (chain_id, block_number),
    -- Lookup by hash for reorg detection
    INDEX idx_block_hash (block_hash)
) COMMENT 'Synced block records';
