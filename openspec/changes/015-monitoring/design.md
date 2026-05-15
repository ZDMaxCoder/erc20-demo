## Context

The platform has multiple failure modes: RPC node down, block sync stuck, nonce gap, hot wallet empty, gas spike, reorg. Each needs different alert severity and response.

## Goals / Non-Goals

**Goals:**
- Health check for every external dependency (DB, Redis, RocketMQ, Ethereum node)
- Business metrics for Prometheus/Grafana dashboards
- AlertService with 10-minute deduplication (avoid alert storms)
- Clear severity levels with different notification channels

**Non-Goals:**
- Not implementing Grafana dashboards (infrastructure team responsibility)
- Not implementing PagerDuty/Slack integration (just MQ-based notification)
- Not implementing log aggregation setup

## Decisions

### Spring Boot Actuator + Micrometer + Prometheus
**Why:** Industry standard. Compatible with any monitoring stack (Prometheus, Datadog, etc.).

### Custom EthereumHealthIndicator
Checks: RPC responds, latest block number, sync lag (current time vs block timestamp).
**Why:** Standard health check doesn't know about Ethereum. Custom indicator provides blockchain-specific health.

### Alert deduplication: same type + same level within 10 minutes = skip
**Why:** A single failure condition can trigger hundreds of alerts per minute (e.g., every sync attempt). Dedup prevents operator fatigue.

### Alert persistence + MQ notification
All alerts persisted to t_alert_record. WARN and CRITICAL also published to PLATFORM_ALERT MQ topic for external notification systems to consume.
**Why:** Persistence enables historical analysis. MQ enables flexible notification routing.

## Implementation Context

**Depends on:**
- Spring Boot Actuator already in dependencies
- Micrometer (included with Spring Boot)
- All business services (for metric instrumentation points)
- RocketMQ (for alert publishing)

**Key metrics:**
```
Counters: deposit.count, withdraw.count, collection.count, block.synced.count, reorg.count
Gauges: hot.wallet.balance, block.sync.delay, pending.withdraw.count, pending.nonce.count
Histograms: deposit.confirm.duration, withdraw.process.duration, gas.price.gwei
```

**Alert scenarios and levels:**
```
CRITICAL: sync delay >30 blocks, reorg, hot wallet critical low, withdraw retry exhausted, reconcile mismatch
WARN: hot wallet low, nonce gap, gas above cap, RPC failover
INFO: large withdrawal pending review
```

**Configuration:**
```yaml
management.endpoints.web.exposure.include: health,info,metrics,prometheus
alert.dedup-interval-minutes: 10
```
