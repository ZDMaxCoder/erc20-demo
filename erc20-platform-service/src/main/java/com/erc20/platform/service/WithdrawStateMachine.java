package com.erc20.platform.service;

import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.result.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class WithdrawStateMachine {

    private final Map<WithdrawStatus, Set<WithdrawStatus>> transitions;

    public WithdrawStateMachine() {
        Map<WithdrawStatus, Set<WithdrawStatus>> map = new EnumMap<WithdrawStatus, Set<WithdrawStatus>>(WithdrawStatus.class);
        map.put(WithdrawStatus.PENDING_REVIEW, EnumSet.of(WithdrawStatus.APPROVED, WithdrawStatus.REJECTED));
        map.put(WithdrawStatus.APPROVED, EnumSet.of(WithdrawStatus.SIGNING));
        map.put(WithdrawStatus.SIGNING, EnumSet.of(WithdrawStatus.BROADCASTING));
        map.put(WithdrawStatus.BROADCASTING, EnumSet.of(WithdrawStatus.PENDING_CONFIRM, WithdrawStatus.FAILED));
        map.put(WithdrawStatus.PENDING_CONFIRM, EnumSet.of(WithdrawStatus.SUCCESS, WithdrawStatus.FAILED));
        map.put(WithdrawStatus.FAILED, EnumSet.of(WithdrawStatus.APPROVED));
        this.transitions = Collections.unmodifiableMap(map);
    }

    public boolean canTransition(WithdrawStatus from, WithdrawStatus to) {
        Set<WithdrawStatus> allowed = transitions.get(from);
        return allowed != null && allowed.contains(to);
    }

    public void assertTransition(WithdrawStatus from, WithdrawStatus to) {
        if (!canTransition(from, to)) {
            throw new BizException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    from.getCode() + " -> " + to.getCode());
        }
    }
}
