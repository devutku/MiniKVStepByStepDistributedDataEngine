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
