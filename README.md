# MiniKV: Lightweight Persistent Key-Value Store

A distributed systems and database storage engine experiment inspired by Martin Kleppmann's *Designing Data-Intensive Applications* (DDIA) and the *Bitcask* architecture.

### Key Highlights
- **Append-Only Log:** High-throughput sequential disk writes without in-place mutations.
- **In-Memory Indexing (KeyDir):** Constant-time $O(1)$ lookups holding key-to-offset metadata in RAM.
- **Embedded HTTP REST API:** Minimal, zero-dependency concurrency-safe HTTP server.
- **Crash Recovery:** Automatic rebuild of the in-memory index from disk during startup.

### Mimari Tasarım: Motor ve API'nin Ayrılması
```text
İstemci (curl / Postman) 
       │
       ▼  HTTP Port: 8080
┌───────────────────────────────┐
│     MiniKVHttpServer          │  <- Thread Pool (İstekleri işleyen iş parçacıkları)
├───────────────────────────────┤
│     Handler Katmanı           │  <- Path & Method yönlendirme (GET / POST)
├───────────────────────────────┤
│     MiniKVStorage             │  <- Senkronize Disk & In-Memory Index Motoru
└───────────────────────────────┘
```
## 🚀 Benchmarks & Performance

Load testing was conducted using **k6** simulating a realistic 80/20 Read-to-Write ratio under concurrent load.

### Test Environment & Workload
- **Workload:** 80% Read (`GET /kv/{key}`), 20% Write (`POST /kv/{key}`)
- **Concurrency:** Up to 50 Virtual Users (VUs) over 50 seconds
- **Key Range:** 1,000 distinct pseudo-random keys

### Test Results Summary

| Metric | Result | Target / Threshold |
| :--- | :--- | :--- |
| **Total Requests** | `27,628` (~552 req/s) | N/A |
| **Success Rate** | `100.00%` (0 failed) | `< 1% Failure` |
| **Average Latency** | `6.32 µs` | N/A |
| **p95 Latency** | `< 1 µs` | `< 50 ms` |
| **p99 Latency (Tail)** | `356.92 µs` (0.35 ms) | `< 100 ms` |
| **Max Latency** | `2.03 ms` | N/A |

> **Architectural Takeaway:** The sub-millisecond p99 latency verifies the efficiency of appending data sequentially to disk combined with $O(1)$ hash-map in-memory index pointer resolutions, completely avoiding random disk seeks during lookups.
