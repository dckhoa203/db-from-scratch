# STEP 3 — Row Lock

## Vấn đề kế thừa từ STEP 2

STEP 2 chứng minh rằng từng `get()` và `put()` có thể thread-safe nhưng business
operation sau vẫn không atomic:

```text
READ → MODIFY → WRITE
```

Hai deposits `+100` cùng đọc balance `1000` có thể cùng ghi `1100`. STEP 3 chỉ
giải quyết một câu hỏi:

> Khi nhiều thread cùng modify một row, ai được chạy và ai phải chờ?

## Từ global lock đến row lock

Giải pháp đơn giản đầu tiên là khóa cả `MiniDatabase`:

```java
public synchronized void depositSync(long accountId, long amount) {
    applyDeposit(accountId, amount);
}
```

Global lock sửa lost update, nhưng khóa quá rộng. Deposit vào Account #1 cũng
chặn deposit vào Account #2 vì monitor thuộc về toàn bộ `MiniDatabase`.

Row lock đặt coordination đúng nơi conflict xảy ra:

```text
Account #1 ── Lock #1
Account #2 ── Lock #2
Account #3 ── Lock #3
```

```java
lockManager.lock(accountId);
try {
    read();
    modify();
    write();
} finally {
    lockManager.unlock(accountId);
}
```

Hai writers cùng row bị serialize; writers trên hai row độc lập vẫn có thể chạy
song song.

## Implementation

`LockManager` giữ một `ReentrantLock` cho mỗi row ID:

```java
private final ConcurrentHashMap<Long, ReentrantLock> locks =
        new ConcurrentHashMap<>();
```

`computeIfAbsent()` bảo đảm các threads xin lock cho cùng row nhận cùng một lock
object. Nếu mỗi thread tạo lock riêng, cả hai đều vào critical section và
protection không còn ý nghĩa.

Lock phải được lấy **trước khi SELECT**. Nếu đọc trước rồi mới lock, snapshot đã
đọc có thể stale trong lúc thread chờ lock:

```text
WRONG: read → lock → modify → write
RIGHT: lock → read → modify → write → unlock
```

`finally` bảo đảm row lock được release ngay cả khi operation ném exception.

## Bộ experiment

### 1. GlobalLockDemo — global lock sửa lost update

Hai threads cùng gọi `depositSync(1, 100)`:

```text
Expected = 1200
Actual   = 1200
Lost update prevented = true
```

Experiment này thể hiện trực tiếp tác dụng của `depositSync()`: `synchronized`
biến toàn bộ method thành một critical section trên instance `MiniDatabase`.

### 2. RowLockDemo — row lock cũng sửa lost update

Hai threads cùng gọi `deposit(1, 100)`:

```text
Expected = 1200
Actual   = 1200
Lost update prevented = true
```

Correctness giống global lock. Khác biệt chỉ xuất hiện khi operations đụng các
row độc lập.

### 3. LockGranularityDemo — chứng minh concurrency

Mỗi operation giữ lock trong 500 ms để làm waiting nhìn thấy được. `sleep()` chỉ
là dụng cụ thí nghiệm, không phải business logic nên có trong production.

| Mechanism | Target | Expected balance | Expected time |
| --- | --- | --- | --- |
| Global lock | Different rows | `1100, 1100` | khoảng 1000 ms |
| Row lock | Same row | `1200` | khoảng 1000 ms |
| Row lock | Different rows | `1100, 1100` | khoảng 500 ms |

Kết quả đã quan sát:

```text
Global lock, different rows = 1005 ms
Row lock, same row          = 1008 ms
Row lock, different rows    = 500 ms
```

Không dùng các số này như benchmark. Điều cần quan sát là hình dạng:

```text
Global lock + different rows  → serialized
Row lock + same row           → serialized
Row lock + different rows     → parallel
```

## Chạy STEP 3

Từ repository root:

```bash
javac -d /tmp/db-from-scratch-classes $(rg --files -g '*.java')

java -cp /tmp/db-from-scratch-classes dbformscratch.step3.GlobalLockDemo
java -cp /tmp/db-from-scratch-classes dbformscratch.step3.RowLockDemo
java -cp /tmp/db-from-scratch-classes dbformscratch.step3.LockGranularityDemo
```

## Mental model

```text
No lock
    └── high concurrency, lost update

Global lock
    └── correctness, unrelated rows block each other

Row lock
    ├── same row      → wait
    └── different row → run concurrently
```

Row lock biến conflicting operations trên **cùng row** thành execution tương
đương serial. Nó không serialize toàn bộ database.

## Blocking, contention và hot row

`ReentrantLock.lock()` là blocking. Nếu 100 requests update cùng Account #1:

```text
Account #1 row lock
    ├── 1 owner executes
    └── 99 waiters
```

Thêm application threads hoặc virtual threads không làm row lock biến mất.
Virtual threads làm waiting rẻ hơn ở application layer, nhưng throughput của
hot row vẫn bị giới hạn bởi một writer tại một thời điểm.

Đây là lý do production có thể đồng thời có CPU thấp, nhiều requests đang chờ
và latency cao: bottleneck nằm ở lock contention chứ không phải compute.

## Lock granularity trade-off

```text
Coarse-grained lock
    + ít lock metadata, dễ reasoning
    - concurrency thấp

Fine-grained row lock
    + independent rows chạy song song
    - nhiều lock metadata, lifecycle phức tạp hơn
```

Registry hiện giữ lock mãi sau lần đầu row được sử dụng. Xóa lock một cách ngây
thơ có thể khiến hai lock objects cùng tồn tại cho một row, nên STEP 3 chấp nhận
trade-off registry tăng dần. Database thật có lock-table lifecycle tinh vi hơn.

`ReentrantLock` mặc định cũng không bảo đảm fairness; waiter đến trước không
nhất thiết acquire trước. STEP 3 chỉ yêu cầu mutual exclusion, chưa giải quyết
fair scheduling.

## Giới hạn có chủ đích

### Lock policy đang được thực thi bằng convention

`select()`, `update()` và `delete()` vẫn public. Caller có thể bypass lock bằng
cách gọi trực tiếp `select → update`. Trong mini project, rule là mọi
read-modify-write phải đi qua method có lock. Một API cứng hơn có thể ẩn các
primitive này hoặc đặt tên chúng là unsafe operations.

### Reader chưa lấy lock

`select()` đọc trực tiếp từ `ConcurrentHashMap`. Reader có thể thấy immutable
`Account` trước hoặc sau update, nhưng STEP 3 chưa cung cấp shared lock,
consistent snapshot, MVCC hay isolation level.

### Global lock chỉ thuộc một MiniDatabase instance

Java `synchronized` khóa monitor của object hiện tại. Hai `MiniDatabase`
instances khác nhau không phối hợp với nhau. Nó chỉ là mô hình minh họa, không
phải distributed/database-wide lock thật.

### Row lock chưa phải transaction

Row lock trả lời:

> Ai được modify state này ngay bây giờ?

Transaction trả lời:

> Những state changes nào phải cùng thành công hoặc cùng thất bại?

Nếu transfer làm `A -= 100`, process crash rồi chưa kịp `B += 100`, row lock
không hoàn tác A và tiền vẫn biến mất.

## Map sang Oracle

Một pessimistic read-modify-write flow có thể dùng:

```sql
SELECT *
FROM ACCOUNT
WHERE ID = 1
FOR UPDATE;
```

Conceptually:

```text
BEGIN
  acquire row lock
  read + validate + modify
  UPDATE
COMMIT
  release lock
```

Oracle giữ lock theo transaction lifecycle. `MiniDatabase` hiện chỉ giữ lock
trong phạm vi một Java method; đây là khác biệt quan trọng.

Critical section phải bao gồm toàn bộ business invariant cần bảo vệ, có thể là
`READ + VALIDATE + CALCULATE + WRITE`, không chỉ câu lệnh update cuối cùng.
Ngược lại, network calls hoặc partner API dưới row lock làm lock duration dài,
tăng wait queue, timeout và retry storm.

## Checkpoint

```text
Shared mutable state
        +
Concurrent writers
        ↓
Exclusive row lock
        ↓
Correctness for conflicting writers
        +
Concurrency for independent rows
        ↓
Waiting / contention / hot row
```

> A row lock prevents conflicting updates to the same row without blocking
> independent rows. Its trade-off is contention: a hot row still allows only
> one writer at a time.

STEP 4 sẽ cố tình crash giữa hai row updates để chứng minh row lock không cung
cấp all-or-nothing, rồi từ đó phát minh transaction, commit và rollback.
