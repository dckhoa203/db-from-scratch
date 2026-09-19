# DB-FROM-SCRATCH

> Build a database from first principles.

## Goal

Không học Database theo kiểu:

```text
ACID
Isolation Level
MVCC
B+Tree
WAL
...
```

rồi cố ghi nhớ definition.

Thay vào đó:

```text
Problem
    ↓
Naive Implementation
    ↓
Break It
    ↓
WHY?
    ↓
Invent Mechanism
    ↓
Implement Mechanism
    ↓
Trade-off
    ↓
Map to Real Database
    ↓
Map to Oracle / Production
```

Core project xuyên suốt:

```text
MiniDatabase
```

Viết bằng Java và tiến hóa dần từ:

```text
Map<Key, Row>
```

thành một mini database có:

```text
Concurrency Control
Transaction
MVCC
WAL
Recovery
Page
Buffer Pool
B+Tree
Query Execution
Connection Pool
Replication
```

---

# PHASE 1 — INVENT A DATABASE

## STEP 1 — Một Database ngu ngốc

### Problem

Ta cần lưu account.

### Naive model

```java
class MiniDatabase {

    private final Map<Long, Account> accounts = new HashMap<>();

    Account select(long id) {
        return accounts.get(id);
    }

    void insert(Account account) {
        accounts.put(account.id(), account);
    }

    void update(Account account) {
        accounts.put(account.id(), account);
    }
}
```

### Build

```text
INSERT
SELECT
UPDATE
DELETE
```

### Mental Model

```text
Database
=
Mutable State Manager
```

### Chưa có

```text
Lock
Transaction
MVCC
WAL
Disk
Index
SQL
Connection
```

### Production Mapping

```text
Map<Long, Account>
        ↓
Oracle Table
ACCOUNT
```

---

# PHASE 2 — CONCURRENCY CONTROL

## STEP 2 — Hai thread cùng UPDATE

### Experiment

```text
Account A = 1000
Account B = 1000
```

Cho nhiều thread chạy:

```text
read
modify
write
```

### Expected

```text
Total money = 2000
```

### Actual

```text
1970
1930
2010
...
```

### Problem

```text
Lost Update
Race Condition
```

### WHY

```text
read → modify → write

không phải một atomic operation
```

### Connection

Map trực tiếp với:

```text
CONCURRENCY-FROM-SCRATCH
```

---

## STEP 3 — Row Lock

### Problem

Nhiều thread cùng modify một row.

### Invent

```text
Row Lock
```

### Model

```text
Thread 1 ─┐
Thread 2 ─┤
Thread 3 ─┼──> Account #1 Lock
Thread 4 ─┤
Thread 5 ─┘
```

### Build

```java
lock(accountId);

try {
    read();
    modify();
    write();
} finally {
    unlock();
}
```

### Concepts

```text
Exclusive Lock
Blocking
Waiting
Contention
Hot Row
```

### Trade-off

```text
Correctness ↑
Concurrency ↓
```

### Production Mapping

```text
Bank account
Wallet balance
Inventory
Counter
Hot business row
```

---

# PHASE 3 — TRANSACTION & ISOLATION

## STEP 4 — Multi-row Transaction

### Scenario

Transfer:

```text
A -= 100
B += 100
```

### Failure

```text
UPDATE A

💥 crash

UPDATE B
```

Result:

```text
A = 900
B = 1000
```

Tiền biến mất.

### Invent

```text
BEGIN

UPDATE A
UPDATE B

COMMIT
```

Nếu lỗi:

```text
ROLLBACK
```

### Build

```text
Transaction
TransactionContext
WriteSet
BeforeImage
```

### Mental Model

```text
Transaction
=
A group of state changes
that appear all-or-nothing
```

### Concept discovered

```text
Atomicity
```

---

## STEP 5 — Isolation Problems

Transaction đã atomic.

Nhưng:

```text
Transaction A
Transaction B
```

có thể nhìn thấy state của nhau như thế nào?

### Reproduce

```text
Dirty Read
Non-repeatable Read
Phantom Read
Lost Update
```

### Example

```text
T1:
SELECT balance
→ 1000

T2:
UPDATE balance = 500
COMMIT

T1:
SELECT balance
→ 500
```

### Question

```text
Một transaction có nên nhìn thấy
state thay đổi giữa chừng không?
```

### Concepts

```text
Read Uncommitted
Read Committed
Repeatable Read
Serializable
```

Isolation level chỉ xuất hiện **sau khi đã nhìn thấy anomaly**.

---

## STEP 6 — Two-Phase Locking

### Problem

Lock từng statement chưa đủ để đảm bảo transaction isolation.

### Model

```text
Transaction
    │
    ├── acquire lock
    ├── acquire lock
    ├── operate
    ├── operate
    │
    └── release at commit
```

### Concepts

```text
Shared Lock
Exclusive Lock
Lock Compatibility
Lock Upgrade
Lock Lifetime
```

### Trade-off

```text
Isolation ↑
Concurrency ↓
Waiting ↑
Deadlock risk ↑
```

---

## STEP 7 — MVCC

### Question

```text
Tại sao SELECT phải đứng chờ UPDATE?
```

### Idea

Giữ nhiều version của cùng một row.

```text
Account #1

V1 → balance = 1000
V2 → balance = 900
V3 → balance = 850
```

### Build

```java
class RowVersion {
    long txId;
    Account value;
    RowVersion previous;
}
```

### Add

```text
Snapshot
Visibility Rule
Transaction ID
```

### Mental Model

```text
Writer
    ↓
create new version

Reader
    ↓
choose visible version
```

### Concepts

```text
MVCC
Snapshot
Version Visibility
Reader vs Writer
```

### Production Mapping

```text
Oracle
PostgreSQL
```

---

## STEP 8 — Deadlock

### Scenario

```text
T1:
lock A
wait B

T2:
lock B
wait A
```

### Model

```text
T1 ─────> T2
↑         │
└─────────┘
```

### Invent

```text
Wait-For Graph
```

### Build

```text
Transaction = Vertex
Wait = Edge
```

Detect:

```text
Cycle
```

### Resolve

```text
Choose victim
Rollback
Retry
```

### DSA Mapping

```text
Graph
Cycle Detection
DFS
```

---

# PHASE 4 — DURABILITY & RECOVERY

## STEP 9 — Database Crash

Cho tới đây:

```text
Database
=
HashMap in RAM
```

Process chết:

```text
💥
```

Data biến mất.

### Naive solution

Mỗi UPDATE:

```text
write entire state to disk
```

### Problem

```text
Disk write expensive
Random I/O
Latency
```

### Question

```text
Có cách nào ghi một thứ nhỏ hơn
nhưng vẫn recover được state?
```

---

## STEP 10 — Write-Ahead Log

### Invent

```text
WAL
```

Example:

```text
TX100 BEGIN
TX100 UPDATE A old=1000 new=900
TX100 UPDATE B old=1000 new=1100
TX100 COMMIT
```

### Rule

```text
Log must become durable
before data page needs to be durable.
```

### Build

```text
LogRecord
WalWriter
TransactionLog
```

### Experiment

```text
Write WAL
COMMIT

💥 crash

Restart
Replay WAL
```

### Concepts

```text
Durability
Redo
Undo
Commit Record
fsync
```

---

## STEP 11 — Recovery & Checkpoint

### Problem

WAL grows:

```text
1 GB
10 GB
100 GB
```

Restart:

```text
Replay from beginning
```

quá chậm.

### Invent

```text
Checkpoint
```

### Model

```text
WAL
──────────────────────────────>

             CHECKPOINT
                 │
                 ▼

Recovery starts near here
```

### Experiments

```text
Crash before COMMIT
Crash after COMMIT
Dirty page not flushed
Dirty page already flushed
```

### Important Mental Model

```text
COMMIT
≠
flush every table page immediately
```

---

# PHASE 5 — STORAGE ENGINE

## STEP 12 — Disk, Page & Record

Bóc abstraction:

```text
Map<Key, Row>
```

xuống:

```text
Database File
    ↓
Page
    ↓
Record
    ↓
Field
```

### Build

```java
class Page {
    long pageId;
    byte[] data;
}
```

### Concepts

```text
Page
Record Layout
Page Size
Sequential I/O
Random I/O
Locality
```

### Key Mental Model

```text
Database thường không đọc
"một row từ disk".

Database đọc PAGE.
```

---

## STEP 13 — Buffer Pool

### Problem

Disk quá chậm để SELECT nào cũng đọc trực tiếp.

### Invent

```text
Buffer Pool
```

### Model

```text
Application
     ↓
Database
     ↓
┌──────────────┐
│ Buffer Pool  │
│ Page 1       │
│ Page 8       │
│ Page 21      │
└──────────────┘
     ↓
    Disk
```

### Build

```text
BufferPool
PageFrame
PageId
```

### Concepts

```text
Cache Hit
Cache Miss
Dirty Page
Flush
Eviction
Pin
```

### Connect

```text
WAL
 +
Dirty Page
 +
Checkpoint
 +
Buffer Pool
```

---

## STEP 14 — Full Table Scan

### Query

```sql
SELECT *
FROM account
WHERE account_number = '123';
```

### Current engine

```text
Page 1
Page 2
Page 3
...
Page 10000
```

### Problem

```text
O(n)
```

### Observation

```text
Most pages are useless for this query.
```

### Question

```text
Làm sao biết row nằm gần đâu
mà không scan toàn bộ table?
```

---

## STEP 15 — B+Tree Index

### Evolution

```text
Linear Scan
    ↓
Sorted Array
    ↓
Binary Search
    ↓
Insert expensive
    ↓
Binary Search Tree
    ↓
Tree too deep for disk
    ↓
High Fan-out Tree
    ↓
B+Tree
```

### Key Mental Model

```text
B+Tree Node
≈
Disk Page
```

### Build

```text
BPlusTree
BPlusNode
IndexEntry
```

### Concepts

```text
Root
Internal Node
Leaf Node
Split
Search
Insert
Range Scan
```

### Extend

```text
Primary Index
Secondary Index
Composite Index
```

### Production Mapping

```text
Oracle Index
Execution Plan
Slow Query
Stored Procedure
```

---

# PHASE 6 — QUERY ENGINE & APPLICATION BOUNDARY

## STEP 16 — SQL Execution Pipeline

Follow:

```sql
SELECT *
FROM account
WHERE id = 10;
```

through:

```text
SQL
 ↓
Tokenizer
 ↓
Parser
 ↓
AST
 ↓
Logical Plan
 ↓
Optimizer
 ↓
Physical Plan
 ↓
Executor
 ↓
Storage Engine
```

### Mini scope

Chỉ support:

```sql
SELECT
FROM
WHERE
```

### Physical operators

```text
Table Scan
Index Scan
Filter
Nested Loop Join
```

### Goal

Hiểu:

```text
Same SQL
≠
Same execution strategy
```

---

## STEP 17 — Connection & Connection Pool

Bây giờ quay trở lại application.

### Request Path

```text
HTTP Request
     ↓
Platform / Virtual Thread
     ↓
HikariCP
     ↓
DB Connection
     ↓
Oracle Session
     ↓
Query
```

### Build Mini Pool

```text
BlockingQueue<Connection>
```

### Experiment

```text
200 application threads
20 DB connections
```

Result:

```text
20 execute
180 wait
```

### Concepts

```text
Pool Size
Connection Wait
Connection Timeout
Slow Query
Long Transaction
Connection Leak
Broken Connection
Backpressure
```

### Production Mapping

```text
Oracle error
Hikari exhaustion
Low CPU
High p99
Many waiting threads
```

---

# PHASE 7 — DISTRIBUTED DATABASE REALITY

## STEP 18 — Replication

### Problem

```text
Primary dies
```

### Idea

Use WAL as replication stream.

```text
Primary
   │
   │ WAL
   ▼
Replica
```

### Concepts

```text
Primary
Replica
Replication Lag
Sync Replication
Async Replication
Failover
```

### Trade-off

```text
Durability
Availability
Latency
Consistency
```

---

## STEP 19 — Partitioning & Sharding

### Problem

Một database node không scale mãi được.

### Explore

```text
Partition
Horizontal Partition
Vertical Partition
Hash Partition
Range Partition
```

Sau đó:

```text
Shard
Shard Key
Hot Partition
Cross-shard Query
Cross-shard Transaction
```

### Production Mapping

```text
Wallet
Account
Partner
Transaction history
```

---

# PHASE 8 — RETURN TO PRODUCTION

## STEP 20 — Outbox Pattern

### Problem

```text
DB UPDATE succeeds
Kafka / MQ publish fails
```

hoặc ngược lại.

### Model

```text
BEGIN

UPDATE business_table
INSERT outbox

COMMIT
```

Sau đó:

```text
Outbox Worker
     ↓
Publish Event
```

### Concepts

```text
Transactional Outbox
At-least-once Delivery
Retry
Idempotency
```

---

## STEP 21 — Idempotency

### Problem

Retry có thể tạo duplicate.

```text
request
   ↓
timeout
   ↓
retry
```

Server có thể đã xử lý request đầu tiên.

### Build

```text
request_id
idempotency_key
unique constraint
processed_event
```

### Production Mapping

```text
Banking transaction
Payment
Wallet
Outbox consumer
Reconciliation
```

---

## STEP 22 — Reconciliation

### Reality

Distributed system không hoàn hảo.

```text
App state
Core state
Partner state
```

có thể lệch nhau.

### Build mental model

```text
Source of Truth
Expected State
Actual State
Diff
Repair
```

### Concepts

```text
Reconciliation Job
Retry
Compensation
Repair
Audit
```

---

# FINAL INTEGRATION

Sau toàn bộ track, trace một request:

```text
POST /transfer
```

qua:

```text
HTTP Request
      ↓
Virtual Thread
      ↓
Hikari Connection Pool
      ↓
Database Connection
      ↓
BEGIN
      ↓
SQL Parser
      ↓
Query Planner
      ↓
B+Tree
      ↓
Buffer Pool
      ↓
Page
      ↓
MVCC / Lock
      ↓
UPDATE
      ↓
WAL
      ↓
COMMIT
      ↓
fsync
      ↓
Response
```

---

# PRODUCTION DEBUGGING MENTAL MODEL

Khi gặp:

```text
p99 = 45s
CPU thấp
500 request threads
20 DB connections
```

Không kết luận ngay:

```text
"DB chậm."
```

Mà follow:

```text
Request đang chờ ở đâu?

│
├── Connection Pool?
│
├── Lock?
│
├── Deadlock?
│
├── Slow Query?
│
├── Bad Execution Plan?
│
├── Full Scan?
│
├── Disk I/O?
│
├── Buffer Pool Miss?
│
├── Hot Row?
│
├── Long Transaction?
│
├── WAL / fsync?
│
└── Network / Connection?
```

---

# MINI DATABASE PROJECT STRUCTURE

Không tạo tất cả ngay từ đầu.

Structure cuối cùng có thể tiến hóa thành:

```text
db-from-scratch/
│
├── database/
│   └── MiniDatabase.java
│
├── model/
│   ├── Account.java
│   └── Row.java
│
├── transaction/
│   ├── Transaction.java
│   ├── TransactionManager.java
│   └── TransactionState.java
│
├── lock/
│   ├── LockManager.java
│   ├── RowLock.java
│   └── DeadlockDetector.java
│
├── mvcc/
│   ├── RowVersion.java
│   ├── Snapshot.java
│   └── VisibilityRule.java
│
├── wal/
│   ├── LogRecord.java
│   ├── WalWriter.java
│   └── RecoveryManager.java
│
├── storage/
│   ├── DiskManager.java
│   ├── Page.java
│   ├── Record.java
│   └── BufferPool.java
│
├── index/
│   ├── Index.java
│   ├── BPlusTree.java
│   └── BPlusNode.java
│
├── query/
│   ├── Query.java
│   ├── QueryPlan.java
│   ├── Planner.java
│   └── Executor.java
│
└── connection/
    ├── MiniConnection.java
    └── MiniConnectionPool.java
```

Rule:

```text
Không tạo abstraction trước khi có problem cần nó.
```

---

# FORMAT CHUẨN CHO MỖI STEP

Mỗi bài DB-FROM-SCRATCH dùng cùng format:

```text
1. Problem

2. Naive Implementation

3. Experiment

4. Make It Fail

5. Observe

6. WHY?

7. Mental Model

8. Invent Mechanism

9. Implement

10. Test Again

11. Trade-off

12. Real Database Mapping

13. Oracle / Production Mapping

14. Interview Takeaway
```

---

# INTERVIEW LAYER

Sau khi hiểu concept bằng tiếng Việt, thêm một đoạn spoken English ngắn.

Example MVCC:

```text
MVCC allows readers to work with a consistent snapshot
instead of always blocking on writers.

The trade-off is that the database needs to maintain
multiple row versions and clean up obsolete versions.
```

Rule:

```text
Vietnamese reasoning first
        ↓
Understand mechanism
        ↓
English technical explanation
```

Không học English definition trước khi hiểu mechanism.

---

# CONNECTION WITH OTHER FROM-SCRATCH TRACKS

```text
FOUNDATION
    │
    ├── Process
    ├── Thread
    ├── Virtual Memory
    └── I/O
          │
          ▼
THREAD-FROM-SCRATCH
    │
    ├── Scheduling
    ├── Worker
    ├── Queue
    ├── Work Stealing
    └── Blocking
          │
          ▼
CONCURRENCY-FROM-SCRATCH
    │
    ├── Shared State
    ├── Race Condition
    └── Lock
          │
          ▼
EVENTLOOP-FROM-SCRATCH
    │
    ├── Non-blocking I/O
    ├── Selector
    ├── Run-to-completion
    └── Worker Offload
          │
          ▼
DB-FROM-SCRATCH
    │
    ├── Transaction
    ├── Lock
    ├── MVCC
    ├── WAL
    ├── Storage
    ├── Index
    └── Query Engine
          │
          ▼
SYSTEM DESIGN
```

---

# TRACK MILESTONES

## Milestone 1 — Correctness

Sau STEP 1–8:

```text
State
Concurrency
Lock
Transaction
Isolation
MVCC
Deadlock
```

Bạn phải answer được:

```text
DB làm sao giữ dữ liệu đúng
khi nhiều transaction chạy cùng lúc?
```

---

## Milestone 2 — Durability

Sau STEP 9–11:

```text
Crash
WAL
Redo
Undo
Checkpoint
Recovery
```

Answer được:

```text
COMMIT thực sự nghĩa là gì
khi máy có thể mất điện bất cứ lúc nào?
```

---

## Milestone 3 — Storage

Sau STEP 12–15:

```text
Disk
Page
Buffer Pool
B+Tree
```

Answer được:

```text
Một row thật sự được lưu và tìm
như thế nào dưới storage layer?
```

---

## Milestone 4 — Query Execution

Sau STEP 16:

```text
SQL
Planner
Execution Plan
Scan
Index
Join
```

Answer được:

```text
Từ SQL text tới dữ liệu trả về
database đã làm những gì?
```

---

## Milestone 5 — Backend Integration

Sau STEP 17–22:

```text
Connection Pool
Replication
Partition
Outbox
Idempotency
Reconciliation
```

Answer được:

```text
Database internals ảnh hưởng như thế nào
tới distributed backend system?
```

---

# FINAL GOAL

Sau DB-FROM-SCRATCH:

```text
Application Developer
        ↓
Database User
        ↓
Database Mental Model
        ↓
Backend/System Engineer
```

Khi gặp một production incident, mục tiêu không phải nhớ:

```text
"Oracle có feature X."
```

Mà reasoning được:

```text
What state exists?

Who owns it?

Who can modify it?

What happens concurrently?

What must be atomic?

What must survive a crash?

Where is the data physically?

How is it found?

Where can the request wait?

What happens when the node dies?

How do systems recover when states diverge?
```

Đó là mental model trung tâm của:

```text
DB-FROM-SCRATCH
```