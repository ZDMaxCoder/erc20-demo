ALTER TABLE t_block_record ADD COLUMN reorged TINYINT NOT NULL DEFAULT 0 COMMENT '1=reorged, 0=normal' AFTER synced;
