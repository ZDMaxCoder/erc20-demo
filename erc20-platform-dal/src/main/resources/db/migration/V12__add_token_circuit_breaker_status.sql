ALTER TABLE t_token_config ADD COLUMN circuit_breaker_status VARCHAR(16) NOT NULL DEFAULT 'CLOSED' COMMENT 'Circuit breaker status: CLOSED/OPEN/HALF_OPEN';
