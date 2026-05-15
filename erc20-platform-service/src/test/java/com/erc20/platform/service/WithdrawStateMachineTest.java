package com.erc20.platform.service;

import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WithdrawStateMachineTest {

    private WithdrawStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new WithdrawStateMachine();
    }

    // --- 1.1 Valid transitions ---

    @Test
    void pendingReview_to_approved_allowed() {
        assertTrue(stateMachine.canTransition(WithdrawStatus.PENDING_REVIEW, WithdrawStatus.APPROVED));
        assertDoesNotThrow(() -> stateMachine.assertTransition(WithdrawStatus.PENDING_REVIEW, WithdrawStatus.APPROVED));
    }

    @Test
    void pendingReview_to_rejected_allowed() {
        assertTrue(stateMachine.canTransition(WithdrawStatus.PENDING_REVIEW, WithdrawStatus.REJECTED));
        assertDoesNotThrow(() -> stateMachine.assertTransition(WithdrawStatus.PENDING_REVIEW, WithdrawStatus.REJECTED));
    }

    @Test
    void approved_to_signing_allowed() {
        assertTrue(stateMachine.canTransition(WithdrawStatus.APPROVED, WithdrawStatus.SIGNING));
        assertDoesNotThrow(() -> stateMachine.assertTransition(WithdrawStatus.APPROVED, WithdrawStatus.SIGNING));
    }

    @Test
    void signing_to_broadcasting_allowed() {
        assertTrue(stateMachine.canTransition(WithdrawStatus.SIGNING, WithdrawStatus.BROADCASTING));
        assertDoesNotThrow(() -> stateMachine.assertTransition(WithdrawStatus.SIGNING, WithdrawStatus.BROADCASTING));
    }

    @Test
    void broadcasting_to_pendingConfirm_allowed() {
        assertTrue(stateMachine.canTransition(WithdrawStatus.BROADCASTING, WithdrawStatus.PENDING_CONFIRM));
        assertDoesNotThrow(() -> stateMachine.assertTransition(WithdrawStatus.BROADCASTING, WithdrawStatus.PENDING_CONFIRM));
    }

    @Test
    void pendingConfirm_to_success_allowed() {
        assertTrue(stateMachine.canTransition(WithdrawStatus.PENDING_CONFIRM, WithdrawStatus.SUCCESS));
        assertDoesNotThrow(() -> stateMachine.assertTransition(WithdrawStatus.PENDING_CONFIRM, WithdrawStatus.SUCCESS));
    }

    @Test
    void broadcasting_to_failed_allowed() {
        assertTrue(stateMachine.canTransition(WithdrawStatus.BROADCASTING, WithdrawStatus.FAILED));
    }

    @Test
    void pendingConfirm_to_failed_allowed() {
        assertTrue(stateMachine.canTransition(WithdrawStatus.PENDING_CONFIRM, WithdrawStatus.FAILED));
    }

    @Test
    void failed_to_approved_allowed_retry() {
        assertTrue(stateMachine.canTransition(WithdrawStatus.FAILED, WithdrawStatus.APPROVED));
        assertDoesNotThrow(() -> stateMachine.assertTransition(WithdrawStatus.FAILED, WithdrawStatus.APPROVED));
    }

    // --- 1.2 Illegal transitions throw BizException ---

    @Test
    void broadcasting_to_success_notAllowed_mustGoViaPendingConfirm() {
        assertFalse(stateMachine.canTransition(WithdrawStatus.BROADCASTING, WithdrawStatus.SUCCESS));
        assertThrows(BizException.class, () -> stateMachine.assertTransition(WithdrawStatus.BROADCASTING, WithdrawStatus.SUCCESS));
    }

    @Test
    void pendingReview_to_broadcasting_notAllowed() {
        assertFalse(stateMachine.canTransition(WithdrawStatus.PENDING_REVIEW, WithdrawStatus.BROADCASTING));
        assertThrows(BizException.class, () -> stateMachine.assertTransition(WithdrawStatus.PENDING_REVIEW, WithdrawStatus.BROADCASTING));
    }

    @Test
    void success_to_any_notAllowed() {
        for (WithdrawStatus target : WithdrawStatus.values()) {
            assertFalse(stateMachine.canTransition(WithdrawStatus.SUCCESS, target));
        }
    }

    @Test
    void rejected_to_any_notAllowed() {
        for (WithdrawStatus target : WithdrawStatus.values()) {
            assertFalse(stateMachine.canTransition(WithdrawStatus.REJECTED, target));
        }
    }

    @Test
    void approved_to_success_notAllowed() {
        assertFalse(stateMachine.canTransition(WithdrawStatus.APPROVED, WithdrawStatus.SUCCESS));
        assertThrows(BizException.class, () -> stateMachine.assertTransition(WithdrawStatus.APPROVED, WithdrawStatus.SUCCESS));
    }
}
