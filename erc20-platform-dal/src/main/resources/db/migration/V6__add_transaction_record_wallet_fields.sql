ALTER TABLE t_transaction_record ADD COLUMN chain_id INT NOT NULL DEFAULT 1 COMMENT 'Chain ID' AFTER tx_hash;
ALTER TABLE t_transaction_record ADD COLUMN tx_type VARCHAR(32) COMMENT 'ERC20_TRANSFER/ETH_TRANSFER' AFTER to_address;
ALTER TABLE t_transaction_record ADD COLUMN block_hash VARCHAR(128) COMMENT 'Confirmed block hash' AFTER block_number;
ALTER TABLE t_transaction_record ADD COLUMN raw_tx TEXT COMMENT 'Signed raw transaction hex' AFTER block_hash;
ALTER TABLE t_transaction_record ADD COLUMN replaced_by_tx_hash VARCHAR(128) COMMENT 'Replacement transaction hash' AFTER raw_tx;
