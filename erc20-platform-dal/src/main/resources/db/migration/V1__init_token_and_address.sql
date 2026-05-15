-- Token configuration table
CREATE TABLE t_token_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_name VARCHAR(64) NOT NULL COMMENT 'Token name, e.g. USDT',
    token_symbol VARCHAR(32) NOT NULL COMMENT 'Token symbol',
    contract_address VARCHAR(64) NOT NULL COMMENT 'ERC-20 contract address',
    decimals INT NOT NULL COMMENT 'Token decimals on chain, e.g. 6 for USDT',
    amount_exponent INT NOT NULL DEFAULT 2 COMMENT 'Internal amount exponent for min-unit storage',
    chain_id INT NOT NULL DEFAULT 1 COMMENT 'Chain ID, 1=Ethereum mainnet',
    min_deposit_amount BIGINT NOT NULL DEFAULT 0 COMMENT 'Minimum deposit amount in min-unit',
    min_withdraw_amount BIGINT NOT NULL DEFAULT 0 COMMENT 'Minimum withdrawal amount in min-unit',
    withdraw_fee_amount BIGINT NOT NULL DEFAULT 0 COMMENT 'Withdrawal fee in min-unit',
    collection_threshold BIGINT NOT NULL DEFAULT 0 COMMENT 'Auto-collection threshold in min-unit',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1=enabled, 0=disabled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Unique on contract address per chain: lookup token by contract
    UNIQUE INDEX uk_chain_contract (chain_id, contract_address),
    -- Lookup enabled tokens
    INDEX idx_enabled (enabled)
) COMMENT 'ERC-20 token configuration';

-- User address table
CREATE TABLE t_user_address (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT 'External user identifier',
    address VARCHAR(64) NOT NULL COMMENT 'Ethereum address assigned to user',
    private_key_enc TEXT NOT NULL COMMENT 'Encrypted private key',
    token_id BIGINT NOT NULL COMMENT 'FK to t_token_config',
    allocated TINYINT NOT NULL DEFAULT 1 COMMENT '1=allocated to user',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Fast lookup: find address for a user+token pair
    UNIQUE INDEX uk_user_token (user_id, token_id),
    -- Reverse lookup: find user by deposit address
    INDEX idx_address (address)
) COMMENT 'User deposit addresses';
