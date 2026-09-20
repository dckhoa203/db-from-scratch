# DB-FROM-SCRATCH

`DB-FROM-SCRATCH` là track foundation chính thức, đứng ngang hàng với `THREAD-FROM-SCRATCH`, `EVENTLOOP-FROM-SCRATCH` và `DSA-FROM-SCRATCH`.

Chúng ta không học Database theo syllabus truyền thống: không bắt đầu bằng định nghĩa ACID, isolation level hay B+Tree. Thay vào đó, ta xây một database rất đơn giản bằng Java, chủ động làm nó thất bại, rồi để từng cơ chế database xuất hiện như một lời giải bắt buộc.

## Core rule

```text
Problem
   ↓
Naive implementation
   ↓
Make it fail
   ↓
WHY?
   ↓
Invent mechanism
   ↓
Implement mechanism
   ↓
Trade-off
   ↓
Map to real database
   ↓
Map to Oracle / production
```

Không hỏi “MVCC là gì?”, mà hỏi:

> Tại sao reader lại phải chờ writer? Có cách nào để reader nhìn thấy một version cũ không?

Từ đó, `versioning` → `snapshot` → `MVCC` xuất hiện tự nhiên.

## Architecture của series

Track gồm **6 phase / 18 step**. Transaction, durability, storage và index được tách thành những bước nhỏ để reasoning tự nhiên hơn.

| Phase | Chủ đề | Mental model chính |
| --- | --- | --- |
| 1 | Database tối giản | Database quản lý state |
| 2 | Concurrency | Nhiều transaction cùng đụng state |
| 3 | Transaction & Isolation | Giữ correctness như thế nào |
| 4 | Durability & Recovery | RAM mất thì state sống thế nào |
| 5 | Storage & Index | Data thật nằm và được tìm thế nào |
| 6 | Execution & Production | SQL, pool, replication, outbox |

## Phase 1 — Invent a Database

### Step 1 — Một Database ngu ngốc

Bắt đầu với `Map<Long, Account>` và chỉ có `INSERT`, `SELECT`, `UPDATE`, `DELETE`.

```java
class MiniDatabase {
    private final Map<Long, Account> accounts = new HashMap<>();

    Account select(long id) { return accounts.get(id); }
    void insert(Account account) { accounts.put(account.id(), account); }
    void update(Account account) { accounts.put(account.id(), account); }
}
```

Chưa có lock, transaction, index, disk, WAL, MVCC, SQL hay connection. Chỉ có `Key → Row`.

Mục tiêu không phải là học database, mà là thấy rằng database trước hết là hệ thống quản lý **mutable state**.

Production mapping: `Map<Long, Account>` → abstraction của Oracle table `ACCOUNT`.

Checkpoint implementation và giới hạn của STEP 1: [step1/README.md](dbfromsractch/src/dbformscratch/step1/README.md).

## Phase 2 — Concurrency Control

### Step 2 — Hai thread cùng UPDATE

Cho 100 threads cùng `read → modify → write` lên account. Không synchronization; tổng tiền kỳ vọng 2000 nhưng kết quả có thể lệch. Ta gặp **lost update** và thấy rằng read-modify-write không atomic.

### Step 3 — Row Lock

Phát minh row-level exclusive lock:

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

Từ đó xuất hiện blocking, waiting, contention và hot row. Experiment: 100 threads cùng một account → 1 row lock → 99 waiters.

Production mapping: bank account, wallet balance, inventory, counter và sequence-like hot data.

## Phase 3 — Transaction & Isolation

### Step 4 — Multi-row Transaction

Transfer gồm `A -= 100` và `B += 100`. Nếu crash giữa hai update, tiền biến mất. Vì vậy xuất hiện `BEGIN`, `COMMIT`, `ROLLBACK`, `TransactionContext`, `WriteSet` và `BeforeImage`.

Transaction không chỉ là JDBC API; nó là một đơn vị thay đổi state phải xuất hiện all-or-nothing. Đây là lúc Atomicity có nghĩa thật.

### Step 5 — Isolation Problems

Tự reproduce dirty read, non-repeatable read, phantom read và lost update trước khi nói về `READ COMMITTED`, `REPEATABLE READ`, `SERIALIZABLE`.

### Step 6 — Two-Phase Locking

```text
transaction
    ├── acquire locks
    ├── operate
    └── release at commit
```

Tìm hiểu lock lifecycle, shared/exclusive lock, lock upgrade và compatibility. Trade-off: correctness tăng, concurrency giảm.

### Step 7 — MVCC

Thay vì bắt `SELECT` chờ `UPDATE`, writer tạo version mới và reader chọn version phù hợp với snapshot.

```java
class RowVersion {
    long txId;
    Account value;
    RowVersion previous;
}
```

Từ đó có version, snapshot, visibility và reader/writer separation. Sau bước này, MVCC trong Oracle/Postgres không còn là magic.

### Step 8 — Deadlock

`T1` lock A rồi chờ B, trong khi `T2` lock B rồi chờ A. Xây một Wait-For Graph nhỏ:

```text
Transaction = Vertex
Wait dependency = Edge
Cycle → Deadlock
```

Sau đó detect cycle, choose victim và rollback. Đây là điểm kết nối trực tiếp với DSA.

## Phase 4 — Durability & Recovery

### Step 9 — Database Crash 💥

HashMap biến mất khi process chết. Ghi toàn bộ database xuống disk sau mỗi update thì random write quá đắt. Ta cần cách khác.

### Step 10 — Write-Ahead Log

Xây `LogRecord`, `WalWriter`, `TransactionLog` và áp dụng rule: log phải durable trước khi data page được xem là durable.

```text
LOG: TX100 UPDATE A old=1000 new=900
LOG: TX100 COMMIT
```

Experiment: write WAL → commit → crash → restart → replay WAL. Từ đây mới nói đến durability, redo, undo, `fsync` và commit record.

### Step 11 — Recovery & Checkpoint

WAL lớn không thể replay từ byte đầu tiên mãi. Phát minh checkpoint, rồi nghiên cứu crash trước/sau commit, dirty page và page flush. Insight: `COMMIT` không có nghĩa ghi ngay mọi table page xuống disk.

## Phase 5 — Storage Engine

### Step 12 — Disk, Page và Row

Đi từ `Map<Key, Row>` xuống `File → Page → Record → Field`. Database thường đọc **page**, không phải một row đơn lẻ. Đây là nền cho random/sequential I/O, page size và locality.

### Step 13 — Buffer Pool

Xây `BufferPool`, `PageFrame`, `PageId`; tìm hiểu cache hit/miss, dirty page, flush, eviction và pin. WAL, buffer pool và checkpoint bắt đầu trở thành một storage engine thống nhất.

### Step 14 — Full Table Scan

Với một triệu account, query theo `account_number` phải scan toàn bộ pages: `O(n)`. Từ vấn đề đó ta buộc phải tìm structure để tra cứu nhanh hơn.

### Step 15 — B+Tree Index

```text
linear scan too slow
  → sorted array
  → binary search
  → insert expensive
  → tree
  → binary tree too deep for disk
  → high fan-out
  → B+Tree
```

Insight chính: **một node xấp xỉ một page**. Sau đó mới tới primary/secondary/composite index, range scan, index design, execution plan và query latency trong Oracle.

## Phase 6 — Query Engine & Production

### Step 16 — SQL đi đâu sau khi gửi vào DB?

Theo dõi `SELECT` qua pipeline:

```text
SQL → Tokenizer / Parser → AST → Logical Plan → Optimizer
    → Physical Plan → Executor → Storage Engine
```

Chỉ cần build subset `SELECT ... FROM ... WHERE ...` để hiểu table scan, index scan, filter, join và vì sao SQL giống nhau có thể tạo execution plan khác nhau.

### Step 17 — Connection & Connection Pool

```text
HTTP request → Java Thread / Virtual Thread → Hikari
             → DB Connection → Oracle session → Query
```

Mô phỏng pool bằng `BlockingQueue<Connection>` rồi load 200 request threads với 20 DB connections. Quan sát pool exhaustion, timeout, slow query, long transaction, broken connection và backpressure.

### Step 18 — Replication → Outbox → Production

Ghép các thành phần lại:

```text
Application → Connection Pool → Transaction Manager
                              ├── Lock / MVCC
                              ├── WAL
                              └── Buffer Pool → Disk
```

Mở rộng đến primary/replica, sync/async replication, replication lag, failover, outbox, idempotency, reconciliation và retry. Mọi incident production—hot row, slow query, connection exhaustion, long transaction, deadlock, replica lag—đều được phân tích bằng các mechanism đã tự xây.

## MiniDatabase tiến hoá dần

Không tạo class khổng lồ hay dựng mọi abstraction ở Step 1. Chỉ thêm abstraction khi vấn đề buộc phải có nó.

```text
db-from-scratch/
├── storage/      Page, DiskManager, BufferPool, Record
├── transaction/  Transaction, TransactionManager, TransactionState
├── lock/         LockManager, RowLock, DeadlockDetector
├── mvcc/         RowVersion, Snapshot, VisibilityRule
├── wal/          LogRecord, WalWriter, RecoveryManager
├── index/        BPlusTree, BPlusNode, Index
├── query/        Query, QueryPlan, Executor
└── MiniDatabase.java
```

Ở Step 1, project có thể chỉ là `MiniDatabase`, `Account` và `Main`.

## Format cố định cho mỗi step

1. Problem thực tế
2. Naive implementation
3. Chạy experiment
4. Quan sát nó sai
5. WHY?
6. Vẽ mental model
7. Tự nghĩ mechanism cần có
8. Implement version đơn giản
9. Test lại
10. Trade-off
11. Real DB làm phức tạp hơn chỗ nào?
12. Map → Oracle
13. Map → production case
14. Interview takeaway

Ví dụ takeaway sau MVCC:

> MVCC allows readers to work with a consistent snapshot instead of always blocking on writers. The trade-off is that the database needs to maintain multiple row versions and clean up obsolete versions.

English đến sau khi đã hiểu mechanism bằng tiếng Việt.

## End goal

Khi hoàn thành series, một request `POST /transfer` có thể được mental trace từ HTTP request, virtual thread, Hikari pool và DB connection; qua transaction, SQL parser/planner, B+Tree, buffer pool, page, row version/lock; đến `UPDATE → WAL → COMMIT → fsync → ACK`.

Khi có incident như `p99 = 45s`, CPU thấp, 500 request threads và 20 DB connections, ta không chỉ nói “DB chậm”. Ta có thể hỏi chính xác request block ở đâu: pool wait, lock wait, disk I/O, buffer miss, query plan, hot row, long transaction, network hay connection failure.

```text
THREAD-FROM-SCRATCH → scheduling, blocking, concurrency
                         ↓
EVENTLOOP-FROM-SCRATCH → I/O, multiplexing, async
                         ↓
DB-FROM-SCRATCH → transaction, concurrency control, storage,
                  recovery, query execution
                         ↓
SYSTEM DESIGN
```

Với background backend/banking, đây là nơi bóc những vấn đề đã gặp trong production từ application layer xuống các abstraction bên dưới.

## Next step

**DB-FROM-SCRATCH — Step 1:** Build một database ngu ngốc bằng Java, bắt đầu với `Map<Long, Row>`, `insert/select/update/delete`, invariant tổng tiền và test harness để chuẩn bị cho Step 2—nơi 100 threads sẽ phá nó.
