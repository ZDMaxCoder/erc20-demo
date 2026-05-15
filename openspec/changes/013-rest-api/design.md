## Context

REST APIs are the entry point for external systems. User APIs are called by the frontend/mobile app. Admin APIs are called by the operations dashboard. Both need authentication but with different permission levels.

## Goals / Non-Goals

**Goals:**
- Clean Controller → Service separation (no business logic in controllers)
- Input validation with meaningful error messages
- Unified response format (Result<T> with error codes)
- API documentation accessible at /doc.html
- Auth filter skeleton (not full implementation)

**Non-Goals:**
- Not implementing full OAuth/JWT authentication (just skeleton)
- Not implementing rate limiting (future change)
- Not implementing WebSocket for real-time updates

## Decisions

### Separate modules for user API and admin API
User API in erc20-platform-api, Admin API in erc20-platform-admin.
**Why:** Different security boundaries. Admin API may be deployed on internal network only. Separate modules allow independent deployment.

### VO/DTO layer between Controller and Service
Controllers accept Request VOs, return Response VOs. Never expose Entity directly.
**Why:** Decouples API contract from database schema. Allows evolving internal model without breaking API.

### Knife4j for documentation
**Why:** Enhanced Swagger UI with better Chinese language support. Common choice in Chinese tech companies.

### Custom @EthAddress validator
Reusable annotation for Ethereum address validation in request parameters.
**Why:** Centralizes validation logic. Clear error messages for invalid addresses.

## Risks / Trade-offs

- [Risk] Auth skeleton is not secure → Mitigation: clearly documented as placeholder, never deploy to production without real auth

## Implementation Context

**Depends on:**
```java
// Services
AddressService.allocateDepositAddress(userId, tokenId)
DepositService (query methods)
WithdrawService.createWithdraw / approve / reject
AccountService.getBalance
AccountFlowService.queryFlows

// From 001
Result<T>, BizException, ErrorCode
```

**User API endpoints:**
```
POST /api/v1/deposit/address
GET  /api/v1/deposit/records
GET  /api/v1/deposit/{id}
POST /api/v1/withdraw/create
GET  /api/v1/withdraw/records
GET  /api/v1/withdraw/{id}
GET  /api/v1/account/balance
GET  /api/v1/account/flows
```

**Admin API endpoints:**
```
GET  /api/admin/v1/withdraw/pending-review
POST /api/admin/v1/withdraw/{id}/approve
POST /api/admin/v1/withdraw/{id}/reject
GET  /api/admin/v1/wallet/balances
POST /api/admin/v1/collection/trigger
GET  /api/admin/v1/block/sync-status
POST /api/admin/v1/nonce/reset
GET  /api/admin/v1/alerts
POST /api/admin/v1/alerts/{id}/handle
POST /api/admin/v1/token/add
PUT  /api/admin/v1/token/{id}/config
```
