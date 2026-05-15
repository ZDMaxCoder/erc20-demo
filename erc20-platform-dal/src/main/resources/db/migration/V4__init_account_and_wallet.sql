-- Account balance table
CREATE TABLE t_account_balance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT 'User identifier',
    token_id BIGINT NOT NULL COMMENT 'FK to t_token_config',
    available_balance BIGINT NOT NULL DEFAULT 0 COMMENT 'Available balance in min-unit',
    frozen_balance BIGINT NOT NULL DEFAULT 0 COMMENT 'Frozen balance in min-unit (pending withdrawals)',
    amount_exponent INT NOT NULL COMMENT 'Exponent for min-unit conversion',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- One balance per user per token
    UNIQUE INDEX uk_user_token (user_id, token_id)
) COMMENT 'User account balances';

-- Account flow table
CREATE TABLE t_account_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT 'User identifier',
    token_id BIGINT NOT NULL COMMENT 'FK to t_token_config',
    flow_type VARCHAR(32) NOT NULL COMMENT 'DEPOSIT/WITHDRAW/WITHDRAW_FEE/COLLECTION',
    flow_direction VARCHAR(16) NOT NULL COMMENT 'IN/OUT',
    amount BIGINT NOT NULL COMMENT 'Flow amount in min-unit',
    amount_exponent INT NOT NULL COMMENT 'Exponent for min-unit conversion',
    balance_before BIGINT NOT NULL COMMENT 'Balance before this flow',
    balance_after BIGINT NOT NULL COMMENT 'Balance after this flow',
    biz_id BIGINT COMMENT 'Associated business record ID',
    idempotent_key VARCHAR(256) NOT NULL COMMENT 'Prevent duplicate flows',
    remark VARCHAR(256) COMMENT 'Optional remark',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Prevent duplicate account flows
    UNIQUE INDEX uk_idempotent_key (idempotent_key),
    -- Query flows by user and token
    INDEX idx_user_token (user_id, token_id),
    -- Query flows by type for reporting
    INDEX idx_flow_type (flow_type)
) COMMENT 'Account balance change history';

-- Wallet config table
CREATE TABLE t_wallet_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    address VARCHAR(64) NOT NULL COMMENT 'Wallet address',
    private_key_enc TEXT NOT NULL COMMENT 'Encrypted private key',
    wallet_type VARCHAR(32) NOT NULL COMMENT 'HOT/COLD/GAS',
    chain_id INT NOT NULL DEFAULT 1 COMMENT 'Chain ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1=enabled, 0=disabled',
    remark VARCHAR(256) COMMENT 'Optional description',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Unique wallet address per chain
    UNIQUE INDEX uk_address_chain (address, chain_id),
    -- Lookup by wallet type
    INDEX idx_wallet_type (wallet_type)
) COMMENT 'Platform wallet configurations (hot/cold/gas)';

-- Collection task table
CREATE TABLE t_collection_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_address VARCHAR(64) NOT NULL COMMENT 'User address to collect from',
    to_address VARCHAR(64) NOT NULL COMMENT 'Hot wallet address to collect to',
    token_id BIGINT NOT NULL COMMENT 'FK to t_token_config',
    amount BIGINT NOT NULL COMMENT 'Collection amount in min-unit',
    amount_exponent INT NOT NULL COMMENT 'Exponent for min-unit conversion',
    idempotent_key VARCHAR(256) NOT NULL COMMENT 'COL_fromAddress_tokenId_blockNumber',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/GAS_SUPPLIED/BROADCASTING/SUCCESS/FAILED',
    tx_hash VARCHAR(128) COMMENT 'Collection transaction hash',
    gas_tx_hash VARCHAR(128) COMMENT 'Gas supply transaction hash',
    error_message TEXT COMMENT 'Error message if FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'Number of retries',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Prevent duplicate collection tasks
    UNIQUE INDEX uk_idempotent_key (idempotent_key),
    -- Query by status for processing
    INDEX idx_status (status),
    -- Query by from address
    INDEX idx_from_address (from_address)
) COMMENT 'Token collection tasks (user addr -> hot wallet)';

-- Alert record table
CREATE TABLE t_alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_level VARCHAR(16) NOT NULL COMMENT 'INFO/WARN/CRITICAL',
    alert_type VARCHAR(64) NOT NULL COMMENT 'Alert category',
    title VARCHAR(256) NOT NULL COMMENT 'Alert title',
    content TEXT COMMENT 'Alert detail content',
    biz_type VARCHAR(32) COMMENT 'Associated business type',
    biz_id BIGINT COMMENT 'Associated business record ID',
    resolved TINYINT NOT NULL DEFAULT 0 COMMENT '1=resolved, 0=unresolved',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Query unresolved alerts by level
    INDEX idx_level_resolved (alert_level, resolved),
    -- Query alerts by time
    INDEX idx_created_at (created_at)
) COMMENT 'System alert records';
