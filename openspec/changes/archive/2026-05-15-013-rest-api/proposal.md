## Why

The platform needs HTTP APIs for user operations (deposit address, withdrawal, balance query) and admin operations (review, monitoring, configuration). APIs must be documented, validated, and secure.

## What Changes

- Implement user-facing REST endpoints (deposit, withdraw, account)
- Implement admin REST endpoints (review, monitoring, config)
- Implement global exception handling and validation
- Integrate Swagger/Knife4j for API documentation

## Capabilities

### New Capabilities

- `user-api`: User-facing deposit, withdrawal, and account APIs
- `admin-api`: Admin review, monitoring, and configuration APIs
- `api-security`: Authentication filter skeleton
- `api-documentation`: Swagger/Knife4j integration

## Impact

- erc20-platform-api and erc20-platform-admin modules
- Depends on: 007-deposit-service, 008-withdraw-service, 009-account-service
