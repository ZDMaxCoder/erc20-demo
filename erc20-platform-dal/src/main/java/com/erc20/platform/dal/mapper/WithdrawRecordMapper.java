package com.erc20.platform.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WithdrawRecordMapper extends BaseMapper<WithdrawRecord> {
}
