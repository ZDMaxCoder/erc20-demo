## 1. API Infrastructure (Setup)

- [x] 1.1 Add Knife4j dependency. Create SwaggerConfig with API info.
- [x] 1.2 Implement GlobalExceptionHandler @RestControllerAdvice: BizException→error code, validation→400, DuplicateKey→idempotent success, OptimisticLock→retry, generic→500.
- [x] 1.3 Create @EthAddress validation annotation + EthAddressValidator.
- [x] 1.4 Create UserAuthFilter (extract userId from header) and AdminAuthFilter (placeholder).

## 2. RED — User API Tests

- [x] 2.1 Write DepositControllerTest (@WebMvcTest): POST /api/v1/deposit/address with valid tokenId → 200 with address in response.
- [x] 2.2 Write test: GET /api/v1/deposit/records with pagination → 200 with PageResult.
- [x] 2.3 Write WithdrawControllerTest: POST /api/v1/withdraw/create with valid request → 200 with withdrawId.
- [x] 2.4 Write test: POST /api/v1/withdraw/create with invalid address → 400 with validation error.
- [x] 2.5 Write test: POST /api/v1/withdraw/create with negative amount → 400.
- [x] 2.6 Write AccountControllerTest: GET /api/v1/account/balance → 200 with BalanceVO.

## 3. GREEN — User API

- [x] 3.1 Create all Request VOs with @Valid annotations: DepositAddressRequest, WithdrawCreateRequest (@NotNull @Positive amount, @EthAddress toAddress, @NotBlank requestId), FlowQueryRequest.
- [x] 3.2 Create all Response VOs: DepositRecordVO, WithdrawRecordVO, BalanceVO, AccountFlowVO.
- [x] 3.3 Implement DepositController: POST address, GET records (paginated), GET detail.
- [x] 3.4 Implement WithdrawController: POST create, GET records, GET detail.
- [x] 3.5 Implement AccountController: GET balance, GET flows.
- [x] 3.6 Run tests — all pass.

## 4. RED — Admin API Tests

- [x] 4.1 Write AdminWithdrawControllerTest: GET pending-review → paginated list. POST approve → 200. POST reject with reason → 200.
- [x] 4.2 Write test: POST nonce/reset → calls NonceManager.resetNonce.

## 5. GREEN — Admin API

- [x] 5.1 Implement AdminWithdrawController: pending-review, approve, reject.
- [x] 5.2 Implement AdminWalletController: wallet balances, trigger collection, nonce reset.
- [x] 5.3 Implement AdminMonitorController: sync status, alerts, handle alert.
- [x] 5.4 Implement AdminTokenController: add token, update config.
- [x] 5.5 Run tests — all pass.

## 6. REFACTOR & Verify

- [x] 6.1 Verify no business logic in controllers — all delegated to services. All tests pass.
- [x] 6.2 Start application, verify /doc.html accessible (Swagger UI).
- [x] 6.3 mvn test passes for api and admin modules.
