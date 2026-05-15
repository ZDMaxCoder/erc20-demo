package com.erc20.platform.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.wallet.TxStatusChangedMessage;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.CollectionTriggerService;
import com.erc20.platform.service.WithdrawService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_TX_STATUS_CHANGED,
        consumerGroup = MqConstants.GROUP_TX_STATUS_WITHDRAW)
public class TxStatusConsumer extends BaseConsumer<TxStatusChangedMessage>
        implements RocketMQListener<MessageExt> {

    private final WithdrawRecordMapper withdrawRecordMapper;
    private final WithdrawService withdrawService;
    private final CollectionTriggerService collectionTriggerService;

    public TxStatusConsumer(RedissonClient redissonClient,
                            WithdrawRecordMapper withdrawRecordMapper,
                            WithdrawService withdrawService,
                            CollectionTriggerService collectionTriggerService) {
        super(redissonClient, MqConstants.GROUP_TX_STATUS_WITHDRAW, TxStatusChangedMessage.class);
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.withdrawService = withdrawService;
        this.collectionTriggerService = collectionTriggerService;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        String json = new String(messageExt.getBody());
        String key = messageExt.getKeys();
        handleMessage(json, key);
    }

    @Override
    protected String getMessageKey(TxStatusChangedMessage message) {
        return message.getTxHash() + ":" + message.getToStatus();
    }

    @Override
    protected void doConsume(TxStatusChangedMessage message) {
        handleWithdrawTxStatus(message);
        collectionTriggerService.onTxStatusChanged(message.getTxHash(), message.getToStatus());
    }

    private void handleWithdrawTxStatus(TxStatusChangedMessage message) {
        WithdrawRecord record = withdrawRecordMapper.selectOne(
                new LambdaQueryWrapper<WithdrawRecord>()
                        .eq(WithdrawRecord::getTxHash, message.getTxHash()));

        if (record == null) {
            return;
        }

        log.info("Tx status change for withdrawal {}: {} -> {}",
                record.getId(), message.getFromStatus(), message.getToStatus());

        if (TxStatus.CONFIRMED.getCode().equals(message.getToStatus())) {
            withdrawService.confirmWithdraw(record.getId(), message.getTxHash(), message.getBlockNumber());
        } else if (TxStatus.FAILED.getCode().equals(message.getToStatus())) {
            withdrawService.failWithdraw(record.getId(), "Transaction failed on-chain");
        }
    }
}
