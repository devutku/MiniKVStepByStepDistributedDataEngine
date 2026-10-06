# MiniKV: Distributed Key-Value Storage Engine

MiniKV is an educational, production-oriented distributed Key-Value store implemented in Java. Inspired by the **Bitcask** storage architecture (from the paper *A Log-Structured Hash Table for Fast Key/Value Data* and Martin Kleppmann's *Designing Data-Intensive Applications*), MiniKV combines an append-only binary log format with an in-memory hash index ($O(1)$ lookup), asynchronous single-leader replication, fault-tolerant backlog queues, and multi-node Docker Compose orchestration.

---

## 🏗️ Architecture Overview

MiniKV decouples storage concerns from networking, replication, and containerization layers:

```text
Client (curl / Postman / k6)
      │
      ▼  (HTTP POST / DELETE / GET)
┌───────────────────────────────────────────────┐
│              MiniKVHttpServer                 │
│  Thread Pool (Concurrent Request Processing)  │
├───────────────────────────────────────────────┤
│               Handler Layer                   │
│   /kv/{key}  |  /compact  |  /internal/*      │
├───────────────────────────────────────────────┤
│               MiniKVStorage                   │
│   • In-Memory KeyDir (ConcurrentHashMap)      │
│   • Append-Only Binary Storage (data.db)      │
│   • Crash Recovery & Compaction Engine        │
└───────────────────────────────────────────────┘
```

### Core Design Principles
* **Append-Only Sequential Writes:** Modifying or appending records writes strictly to the end of the active data log (`RandomAccessFile`), avoiding expensive random disk seeks.
* **In-Memory Index (KeyDir):** All keys are mapped to their disk coordinates `(offset, valueLength)` in RAM. A `GET` requires exactly one disk seek and read.
* **Tombstone Deletion & Merge/Compaction:** Deletions append a tombstone record (`valueLength = -1`). An offline/background compaction sweep filters dead revisions and rebuilds an optimized database file.
* **Crash Recovery:** On startup, the engine scans the log sequentially from offset 0 to rebuild the exact in-memory KeyDir state.

---

## 🌐 Distributed Replication & Fault Tolerance

MiniKV implements a **Single-Leader Asynchronous Replication** model with an internal backlog queue:

```text
                       Client Writes
                            │
                            ▼ (Port 8080)
                 ┌─────────────────────┐
                 │    Leader Node      │
                 │   (leader_data.db)  │
                 └──────────┬──────────┘
                            │ Asynchronous Queue
                            │ (BlockingQueue + Retry Worker)
                            ▼ (/internal/replicate)
                 ┌─────────────────────┐
                 │    Follower Node    │
                 │  (follower_data.db) │
                 └─────────────────────┘
                            ▲
                            │ Client Reads
                            │ (Port 8081)
```

1. **Leader Node (`:8080`):** Handles all write (`POST`), update, and delete (`DELETE`) traffic. Every modification is persisted locally and placed onto a FIFO `BlockingQueue`.
2. **Follower Node (`:8081`):** Serves read traffic (`GET`). Direct writes to follower nodes are blocked via safety guardrails (`403 Forbidden`).
3. **Fault-Tolerant Retry Worker:** If the follower node crashes or experiences network disconnection, the leader keeps serving writes without blocking clients. An internal worker thread continuously retries flushing pending replication tasks until the follower recovers (**Eventual Consistency**).

---

## 🐳 Docker Compose Orchestration

The cluster can be spun up in fully isolated containers across a dedicated internal bridge network:

```yaml
services:
  leader:
    build: .
    container_name: minikv-leader
    ports: ["8080:8080"]
    environment:
      - PORT=8080
      - ROLE=LEADER
      - DATA_FILE=/app/data/leader_data.db
      - FOLLOWER_URL=http://follower:8081

  follower:
    build: .
    container_name: minikv-follower
    ports: ["8081:8081"]
    environment:
      - PORT=8081
      - ROLE=FOLLOWER
      - DATA_FILE=/app/data/follower_data.db
```

### Running with Docker:
```bash
docker compose up --build
```

---

## 🚀 Benchmarks & Performance Verification

Load testing was conducted with **k6** simulating a realistic 80/20 Read-to-Write traffic profile under sustained concurrent pressure:

* **Workload:** 80% Read (`GET /kv/{key}`), 20% Write (`POST /kv/{key}`)
* **Concurrency:** Up to 50 Virtual Users (VUs) over 50 seconds
* **Keyspace:** 1,000 distinct pseudo-random keys

| Metric | Result | Target / Threshold |
| :--- | :--- | :--- |
| **Total Requests** | `27,628` (~552 req/s) | N/A |
| **Success Rate** | `100.00%` (0 failed) | `< 1% Failure` |
| **Average Latency** | `6.32 µs` | N/A |
| **p95 Latency** | `< 1 µs` | `< 50 ms` |
| **p99 Tail Latency** | `356.92 µs` (0.35 ms) | `< 100 ms` |
| **Max Latency** | `2.03 ms` | N/A |

---

## 🛠️ API Quickstart

### 1. Write Data to Leader
```bash
curl -X POST http://localhost:8080/kv/engineer -d "Utku"
# Response: OK: engineer kaydedildi. (HTTP 201)
```

### 2. Read Data from Follower
```bash
curl -X GET http://localhost:8081/kv/engineer
# Response: Utku (HTTP 200)
```

### 3. Verify Single-Leader Guardrail
```bash
curl -X POST http://localhost:8081/kv/illegal -d "Payload"
# Response: Forbidden: Follower dugumune dogrudan yazilamaz... (HTTP 403)
```

### 4. Trigger Log Compaction
```bash
curl -X POST http://localhost:8080/compact
```

---

## 📈 Roadmap & Completed Milestones
- [x] Append-only binary disk storage & $O(1)$ in-memory index
- [x] Tombstone-based deletion and sequential log compaction
- [x] Built-in multi-threaded HTTP server
- [x] k6 performance & p99 tail-latency benchmarking
- [x] Single-Leader asynchronous replication with safety guardrails
- [x] Fault-tolerant replication task queue & retry worker
- [x] Multi-stage Dockerfile and Docker Compose orchestration