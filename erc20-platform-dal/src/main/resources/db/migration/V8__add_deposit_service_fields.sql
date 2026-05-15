-- UserAddress: add address_index, status; drop allocated; make user_id/token_id nullable for pool addresses
ALTER TABLE t_user_address ADD COLUMN address_index INT NULL COMMENT 'BIP-44 derivation index';
ALTER TABLE t_user_address ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/BOUND/DISABLED';
ALTER TABLE t_user_address MODIFY COLUMN user_id VARCHAR(64) NULL;
ALTER TABLE t_user_address MODIFY COLUMN token_id BIGINT NULL;
ALTER TABLE t_user_address DROP INDEX uk_user_token;
ALTER TABLE t_user_address DROP COLUMN allocated;
ALTER TABLE t_user_address ADD UNIQUE INDEX uk_address (address);

-- DepositRecord: add credited flag
ALTER TABLE t_deposit_record ADD COLUMN credited TINYINT NOT NULL DEFAULT 0 COMMENT '1=balance credited, 0=not yet';

-- TokenConfig: add deposit_confirm_blocks
ALTER TABLE t_token_config ADD COLUMN deposit_confirm_blocks INT NOT NULL DEFAULT 12 COMMENT 'Required confirmations for deposit';
