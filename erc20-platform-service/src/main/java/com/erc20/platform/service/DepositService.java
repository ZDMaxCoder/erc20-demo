package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.common.enums.FlowType;
import com.erc20.platform.common.enums.TokenType;
import com.erc20.platform.common.exception.AmountOverflowException;
import com.erc20.platform.common.util.AddressUtil;
import com.erc20.platform.common.util.AmountUtil;
import com.erc20.platform.common.util.IdempotentKeyGenerator;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.UserAddressMapper;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.UserAddress;
import com.erc20.platform.service.dto.AccountOperateRequest;
import com.erc20.platform.service.dto.TransferEventDTO;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class DepositService {

    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private final DepositRecordMapper depositRecordMapper;
    private final TokenConfigMapper tokenConfigMapper;
    private final UserAddressMapper userAddressMapper;
    private final AccountService accountService;
    private final AlertService alertService;
    private final BusinessMetrics businessMetrics;

    public DepositService(DepositRecordMapper depositRecordMapper,
                          TokenConfigMapper tokenConfigMapper,
                          UserAddressMapper userAddressMapper,
                          AccountService accountService,
                          AlertService alertService,
                          BusinessMetrics businessMetrics) {
        this.depositRecordMapper = depositRecordMapper;
        this.tokenConfigMapper = tokenConfigMapper;
        this.userAddressMapper = userAddressMapper;
        this.accountService = accountService;
        this.alertService = alertService;
        this.businessMetrics = businessMetrics;
    }

    public void onTransferEvent(TransferEventDTO event) {
        String normalizedFrom = AddressUtil.normalize(event.getFrom());
        if (ZERO_ADDRESS.equals(normalizedFrom)) {
            log.info("Mint event skipped: txHash={}", event.getTxHash());
            return;
        }

        String normalizedTo = AddressUtil.normalize(event.getTo());

        UserAddress userAddress = userAddressMapper.selectOne(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getAddress, normalizedTo));
        if (userAddress == null) {
            log.debug("Transfer to non-platform address {}, skipping", normalizedTo);
            return;
        }

        String normalizedContract = AddressUtil.normalize(event.getContractAddress());
        TokenConfig tokenConfig = tokenConfigMapper.selectOne(
                new LambdaQueryWrapper<TokenConfig>()
                        .eq(TokenConfig::getContractAddress, normalizedContract)
                        .eq(TokenConfig::getEnabled, 1));
        if (tokenConfig == null) {
            log.debug("Transfer for unknown/disabled contract {}, skipping", normalizedContract);
            return;
        }

        if (!TokenType.STANDARD.getCode().equals(tokenConfig.getTokenType())) {
            log.info("Unsupported token type {}: contract={}, skipping",
                    tokenConfig.getTokenType(), normalizedContract);
            return;
        }

        String idempotentKey = IdempotentKeyGenerator.depositKey(event.getTxHash(), event.getLogIndex());
        DepositRecord existing = depositRecordMapper.selectOne(
                new LambdaQueryWrapper<DepositRecord>()
                        .eq(DepositRecord::getIdempotentKey, idempotentKey));
        if (existing != null) {
            log.info("Duplicate deposit event ignored: {}", idempotentKey);
            return;
        }

        long amount;
        String status;
        try {
            amount = AmountUtil.fromChainAmount(event.getValue(),
                    tokenConfig.getDecimals(), tokenConfig.getAmountExponent());

            if (amount < tokenConfig.getMinDepositAmount()) {
                status = DepositStatus.BELOW_MINIMUM.getCode();
                log.info("Deposit below minimum: amount={}, min={}", amount, tokenConfig.getMinDepositAmount());
            } else {
                status = DepositStatus.CONFIRMING.getCode();
            }
        } catch (AmountOverflowException e) {
            amount = 0L;
            status = DepositStatus.AMOUNT_OVERFLOW.getCode();
            alertService.alert("DEPOSIT_OVERFLOW", AlertLevel.CRITICAL,
                    "Deposit amount overflow: txHash=" + event.getTxHash() + ", value=" + event.getValue(),
                    event.getTxHash());
            log.error("Deposit amount overflow: txHash={}, value={}", event.getTxHash(), event.getValue());
        }

        DepositRecord record = DepositRecord.builder()
                .txHash(event.getTxHash())
                .logIndex(event.getLogIndex())
                .idempotentKey(idempotentKey)
                .userId(userAddress.getUserId())
                .tokenId(tokenConfig.getId())
                .fromAddress(normalizedFrom)
                .toAddress(normalizedTo)
                .amount(amount)
                .amountExponent(tokenConfig.getAmountExponent())
                .status(status)
                .blockNumber(event.getBlockNumber())
                .blockHash(event.getBlockHash() != null ? event.getBlockHash() : "")
                .confirmations(0)
                .requiredConfirmations(tokenConfig.getDepositConfirmBlocks())
                .credited(0)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        depositRecordMapper.insert(record);
        log.info("Deposit record created: txHash={}, user={}, amount={}, status={}",
                event.getTxHash(), userAddress.getUserId(), amount, status);
    }

    @Transactional
    public void creditDeposit(long depositId) {
        DepositRecord deposit = depositRecordMapper.selectById(depositId);
        if (deposit == null) {
            log.warn("Deposit not found: {}", depositId);
            return;
        }

        if (!DepositStatus.CONFIRMING.getCode().equals(deposit.getStatus())
                || deposit.getCredited() == 1) {
            log.info("Deposit {} already credited or not in CONFIRMING status, skipping", depositId);
            return;
        }

        String idempotentKey = "DEPOSIT_CREDIT_" + deposit.getId();
        AccountOperateRequest request = AccountOperateRequest.builder()
                .userId(deposit.getUserId())
                .tokenId(deposit.getTokenId())
                .amount(deposit.getAmount())
                .amountExponent(deposit.getAmountExponent())
                .flowType(FlowType.DEPOSIT)
                .bizId(deposit.getId())
                .idempotentKey(idempotentKey)
                .build();

        accountService.increaseAvailable(request);

        deposit.setStatus(DepositStatus.SUCCESS.getCode());
        deposit.setCredited(1);
        deposit.setUpdatedAt(new Date());
        depositRecordMapper.updateById(deposit);

        businessMetrics.incrementDeposit();
        log.info("Deposit {} credited: user={}, amount={}", depositId,
                deposit.getUserId(), deposit.getAmount());
    }

    public void handleReorg(List<Long> affectedBlockNumbers) {
        List<DepositRecord> affected = depositRecordMapper.selectList(
                new LambdaQueryWrapper<DepositRecord>()
                        .in(DepositRecord::getBlockNumber, affectedBlockNumbers)
                        .ne(DepositRecord::getStatus, DepositStatus.REORGED.getCode()));

        for (DepositRecord deposit : affected) {
            if (deposit.getCredited() == 1) {
                String idempotentKey = "DEPOSIT_REORG_" + deposit.getId();
                AccountOperateRequest request = AccountOperateRequest.builder()
                        .userId(deposit.getUserId())
                        .tokenId(deposit.getTokenId())
                        .amount(deposit.getAmount())
                        .amountExponent(deposit.getAmountExponent())
                        .flowType(FlowType.ADJUSTMENT)
                        .bizId(deposit.getId())
                        .idempotentKey(idempotentKey)
                        .build();
                accountService.decreaseAvailable(request);
                log.warn("Reversed credited deposit {} due to reorg", deposit.getId());
            }

            deposit.setStatus(DepositStatus.REORGED.getCode());
            deposit.setUpdatedAt(new Date());
            depositRecordMapper.updateById(deposit);
        }
    }
}
