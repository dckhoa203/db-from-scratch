# STEP 5 — Isolation Problems

## Câu hỏi mới sau Atomicity

STEP 4 đảm bảo một transaction không để lại partial state khi nó fail. Nhưng
nhiều transactions cùng chạy vẫn có thể đọc hoặc ghi state của nhau theo cách
sai.

```text
Atomicity: một transaction có partial success không?
Isolation: một transaction được thấy state nào của transaction khác?
```

Mental model của STEP 5:

```text
Transaction + concurrent transaction
        ↓
visibility and overwrite problems
        ↓
isolation
```

## Cách đọc các demos

`T1` và `T2` trong `IsolationProblemDemo` là hai `TransactionContext`. Demos
chủ động thực hiện interleaving theo một timeline xác định thay vì dựa vào Java
thread scheduler. Điều này giúp mỗi anomaly luôn tái hiện được với cùng output.

STEP 5 có bản copy riêng của `Account`, `MiniDatabase`, `TransactionContext`,
`TransactionManager` và `TransactionState` trong package `dbformscratch.step5`.
STEP 4 giữ đúng checkpoint atomic multi-row update; STEP 5 mới thêm predicate
scan và transactional insert để làm các isolation anomaly visible. Code trùng
là chủ ý: mỗi step là một snapshot độc lập của database từ scratch.

```text
step5/
├── database/MiniDatabase.java
├── model/Account.java
├── transaction/TransactionContext.java
├── transaction/TransactionManager.java
├── transaction/TransactionState.java
└── IsolationProblemDemo.java
```

Mỗi timeline in các event quan trọng:

```text
BEGIN
SELECT
BEFORE_IMAGE / WRITE_SET
UPDATE hoặc INSERT
COMMIT hoặc ROLLBACK
FINAL STATE
```

## Demos hiện có

| Demo | Điều quan sát được | Kết quả |
| --- | --- | --- |
| Dirty Read | T2 đọc write chưa commit của T1 | T2 thấy `500`, rồi T1 rollback về `1000` |
| Non-repeatable Read | T1 đọc cùng row hai lần, giữa hai reads T2 commit | `1000 → 500` |
| Phantom Read | T1 query lại predicate sau khi T2 insert và commit | `[A] → [A, C]` |
| Lost Update | T2 ghi từ stale read và overwrite write đã commit của T1 | final `950` |
| Rollback overwrites committed write | T1 rollback restore before image sau T2 commit | T2 đã commit `800`, final lại là `1000` |

Demo cuối là một giới hạn của mini database, không phải một tên anomaly chuẩn.
Nó cho thấy before-image rollback đúng trong single transaction nhưng chưa đủ
khi transactions cùng ghi một row.

## 1. Dirty Read

```text
T1: BEGIN
T1: UPDATE A: 1000 → 500

T2: BEGIN
T2: SELECT A → 500

T1: ROLLBACK
T1: RESTORE A: 500 → 1000
```

T2 đã đọc `500` dù value này chưa commit và cuối cùng không tồn tại trong
committed history.

```text
Dirty Read = read data written by another transaction before it commits.
```

## 2. Non-repeatable Read

```text
T1: BEGIN
T1: SELECT A → 1000

T2: BEGIN
T2: UPDATE A: 1000 → 500
T2: COMMIT

T1: SELECT A → 500
```

Cùng transaction, cùng row, cùng read nhưng value khác. Khác với dirty read,
write của T2 đã commit trước lần đọc thứ hai của T1.

## 3. Phantom Read

T1 query toàn bộ accounts có balance ít nhất `1000`:

```text
T1: SELECT balance >= 1000 → [A]

T2: INSERT C balance=2000
T2: COMMIT

T1: SELECT balance >= 1000 → [A, C]
```

Row C xuất hiện trong result set của cùng predicate. Đây là phantom read:

```text
Non-repeatable read → same row, different value
Phantom read        → same predicate, different row set
```

STEP 5 bổ sung transactional insert tối thiểu để T2 thực sự `INSERT` rồi
`COMMIT`; rollback sẽ remove row được insert bởi transaction đó.

## 4. Lost Update

```text
T1: SELECT A → 1000
T2: SELECT A → 1000

T1: UPDATE A → 900; COMMIT
T2: UPDATE A → 950; COMMIT

FINAL A = 950
```

T2 dùng stale value `1000` và ghi đè write `900` của T1. STEP 2 đã cho thấy
cùng lỗi này ở thread level; STEP 5 nhìn nó qua transaction visibility và
write ordering.

## 5. Rollback overwrite: cầu nối sang STEP 6

```text
T1: UPDATE A: 1000 → 900, chưa commit
T2: UPDATE A: 900 → 800; COMMIT
T1: ROLLBACK → restore before image A=1000

FINAL A = 1000
```

Rollback của T1 đã xóa write `800` đã commit của T2. Điều này xảy ra vì T1 và
T2 cùng update row mà không có lock được giữ xuyên suốt transaction.

Row lock ở STEP 3 chỉ sống trong một method. STEP 6 sẽ chuyển sang transaction
lock lifetime: acquire lock, giữ nó qua các statements, release khi commit hoặc
rollback.

## Isolation levels xuất hiện vì các failure này

| Requirement | Hướng mechanism |
| --- | --- |
| Không đọc uncommitted data | Read Committed hoặc mạnh hơn |
| Cùng row có stable view | Repeatable Read hoặc snapshot tương đương |
| Cùng predicate có stable result set | Stronger predicate/range protection hoặc Serializable |
| Không overwrite stale write | Locking, validation, version check, hoặc Serializable semantics |

Chi tiết từng engine khác nhau. Ví dụ phantom behavior của `REPEATABLE READ`
khác giữa Oracle, PostgreSQL, MySQL/InnoDB và SQL standard. Ở step này, điều
cần giữ là vấn đề buộc database phải chọn visibility policy.

## Chạy STEP 5

Từ repository root:

```bash
javac -d /tmp/db-from-scratch-classes $(rg --files -g '*.java')
java -cp /tmp/db-from-scratch-classes dbformscratch.step5.IsolationProblemDemo
```

Output có đánh số từng event để đối chiếu với timeline. Mỗi anomaly kết thúc
bằng boolean `... reproduced = true` hoặc, với rollback conflict,
`Committed write overwritten by rollback = true`.

## Scope của mini database

MiniDatabase áp dụng writes trực tiếp vào shared `ConcurrentHashMap`, nên nó
chủ động cho phép anomalies xuất hiện. Nó chưa có snapshot, shared lock,
exclusive lock theo transaction, predicate lock, MVCC, deadlock handling hay
durable recovery.

`TransactionContext` hiện expose before images và write set để dễ quan sát
trong series. Database engine thật sẽ encapsulate state này chặt hơn.

Exception rollback của STEP 4 vẫn khác process crash. Nếu JVM chết, in-memory
before images biến mất và rollback không thể chạy; WAL/recovery chỉ đến ở STEP
9–11.

## Map sang production

Một fee charging flow có thể đọc balance `100`, validate fee `80`, rồi trong
lúc đó transaction khác debit `50` và commit. Câu hỏi không còn là “SQL có
chạy được không?” mà là business decision đang dựa trên snapshot nào và giả
định đó được bảo vệ thế nào.

Isolation cũng có cost. Nếu giữ lock lâu để bảo vệ reads và writes, other
transactions wait; hot row dẫn đến contention, latency và timeout. STEP 6 sẽ
xây lock compatibility và lifetime để thấy trade-off này trực tiếp.

## Checkpoint

```text
STEP 4
    transaction either commits or rolls back

STEP 5
    concurrent transactions can still see or overwrite wrong state

STEP 6
    locks belong to a transaction and live until transaction end
```

> Atomicity tells us whether one transaction leaves partial changes. Isolation
> tells us what concurrent transactions may see and how their reads and writes
> are allowed to interact.
