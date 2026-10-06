# MiniKV: Distributed Key-Value Storage Engine

MiniKV is an educational, production-oriented distributed Key-Value store implemented in Java. Inspired by the **Bitcask** storage architecture (from the paper *A Log-Structured Hash Table for Fast Key/Value Data* and Martin Kleppmann's *Designing Data-Intensive Applications*), MiniKV combines an append-only binary log format with an in-memory hash index ($O(1)$ lookup) and asynchronous single-leader replication.

---

## 🏗️ Architecture Overview

MiniKV decouples storage concerns from networking and replication layers:

```text
Client (curl / Postman)
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

## 🌐 Distributed Replication Architecture

MiniKV implements a **Single-Leader Asynchronous Replication** model to ensure high read scalability and fault isolation:

```text
                       Client Writes
                            │
                            ▼ (Port 8080)
                 ┌─────────────────────┐
                 │    Leader Node      │
                 │   (leader_data.db)  │
                 └──────────┬──────────┘
                            │ Asynchronous
                            │ Replication Stream
                            ▼ (/internal/replicate)
                 ┌─────────────────────┐
                 │    Follower Node    │
                 │  (follower_data.db) │
                 └─────────────────────┘
                            ▲
                            │ Client Reads
                            │ (Port 8081)
```

1. **Leader Node (`:8080`):** Handles all write (`POST`), update, and delete (`DELETE`) traffic. Every modification is persisted locally and broadcast asynchronously to follower nodes.
2. **Follower Node (`:8081`):** Serves read traffic (`GET`). Direct writes to follower nodes are blocked via safety guardrails (`403 Forbidden`).
3. **Internal Stream:** Replication packets (`ACTION:KEY:VALUE`) update the follower's private disk log and in-memory index independently.

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

## 🛠️ Quickstart & Cluster Execution

### 1. Run the Cluster
Compile and execute `ClusterMain.java` to start both the Leader (`8080`) and Follower (`8081`) simultaneously:

```bash
# Outputs:
# [LEADER] MiniKV HTTP Sunucusu ayakta: http://localhost:8080
# [FOLLOWER] MiniKV HTTP Sunucusu ayakta: http://localhost:8081
# >>> Kume (Cluster) hazir: Leader (8080), Follower (8081) <<<
```

### 2. Write Data to Leader
```bash
curl -X POST [http://127.0.0.1:8080/kv/engineer](http://127.0.0.1:8080/kv/engineer) -d "Utku"
# Response: OK: engineer kaydedildi. (HTTP 201)
```

### 3. Read Data from Follower
```bash
curl -X GET [http://127.0.0.1:8081/kv/engineer](http://127.0.0.1:8081/kv/engineer)
# Response: Utku (HTTP 200)
```

### 4. Verify Single-Leader Guardrail
```bash
curl -X POST [http://127.0.0.1:8081/kv/illegal](http://127.0.0.1:8081/kv/illegal) -d "Payload"
# Response: Forbidden: Follower dugumune dogrudan yazilamaz... (HTTP 403)
```

### 5. Run Compaction
Trigger manual merge/compaction on the active log file:
```bash
curl -X POST [http://127.0.0.1:8080/compact](http://127.0.0.1:8080/compact)
```

---

## 📈 Roadmap & Next Iterations
- [x] Append-only binary disk storage & $O(1)$ in-memory index
- [x] Tombstone-based deletion and sequential log compaction
- [x] Built-in multi-threaded HTTP server
- [x] k6 performance & p99 tail-latency benchmarking
- [x] Single-Leader asynchronous replication with safety guardrails
- [ ] Fault-tolerance: Replication backlog retry queue on follower downtime
- [ ] Multi-stage Dockerfile and Docker Compose orchestration
