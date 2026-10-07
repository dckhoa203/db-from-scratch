# STEP 6 — Two-Phase Locking (Rigorous Model)

## Câu hỏi mới sau STEP 5

STEP 4 cho transaction khả năng `COMMIT` hoặc `ROLLBACK`. STEP 5 cho thấy
atomicity vẫn chưa đủ: một transaction khác có thể nhìn thấy hoặc ghi đè state
vào đúng lúc transaction đầu tiên đang reasoning.

```text
T1: SELECT A -> 1000
T2: UPDATE A -> 500; COMMIT
T1: SELECT A -> 500
```

T1 đọc cùng một row hai lần nhưng nhận hai value khác nhau. Đó là
**non-repeatable read**.

Vấn đề không phải `SELECT` thiếu lock. Vấn đề là lock của STEP 3 chỉ sống trong
một method:

```text
lock A -> SELECT -> unlock A
```

T1 đã dùng `A = 1000` để ra quyết định, nhưng lại không bảo vệ giả định đó
trong phần còn lại của transaction.

## Mental model

STEP 6 đổi ownership của lock:

```text
STEP 3                         STEP 6
-------                        ------
method owns lock               transaction owns lock

lock -> operation -> unlock    BEGIN
                                acquire lock(s)
                                read / write / read
                                COMMIT or ROLLBACK
                                release lock(s)
```

```text
Lock lifetime = transaction lifetime
```

Implementation này giữ **toàn bộ** shared lẫn exclusive lock đến khi
transaction kết thúc. Tên chính xác cho rule mạnh này là **rigorous 2PL**. Nó
thỏa strict 2PL (strict 2PL yêu cầu giữ `X` lock đến transaction end), nhưng
còn chặt hơn vì `S` lock cũng không được release sớm. Đây là model dễ nhìn nhất
để thấy isolation, blocking và contention.

## Cơ chế được phát minh

### 1. Shared và Exclusive lock

Không nên dùng exclusive lock cho mọi `SELECT`, vì hai reader không thay đổi
state của nhau. Vì vậy row lock có hai mode:

| Operation | Lock | Ý nghĩa |
| --- | --- | --- |
| `SELECT` | Shared (`S`) | nhiều reader có thể cùng giữ |
| `UPDATE` | Exclusive (`X`) | chỉ một writer; chặn reader và writer khác |

Compatibility matrix:

| Lock đang giữ | Request `S` | Request `X` |
| --- | --- | --- |
| None | grant | grant |
| `S` | grant | wait |
| `X` | wait | wait |

Nói cách khác:

```text
S + S = compatible
S + X = conflict
X + S = conflict
X + X = conflict
```

### 2. Lock upgrade

Một transaction có thể đọc rồi mới quyết định ghi. Nó đã giữ `S(A)` và cần
upgrade thành `X(A)`.

```text
T1: S(A), SELECT A
T1: X(A), UPDATE A
```

Upgrade được grant ngay khi T1 là shared holder duy nhất. Nếu reader khác vẫn
giữ `S(A)`, request upgrade phải wait.

### 3. Hai phase

Trong 2PL cơ bản:

```text
Growing phase   : only acquire / upgrade locks
Shrinking phase : only release locks
```

MiniDatabase này không release lock sớm. Nó release tất cả tại `COMMIT` hoặc
`ROLLBACK`, nên rigorous 2PL tránh việc transaction khác đọc hoặc ghi qua state
chưa commit của transaction đang giữ `X` lock.

## Code snapshot

STEP 6 có bản copy riêng của database và transaction code. Sự trùng lặp là chủ
ý: mỗi step là một database snapshot độc lập, không vô tình nhận mechanism từ
step sau.

```text
step6/
├── database/MiniDatabase.java      # SELECT -> S, UPDATE -> X
├── lock/RowLock.java               # compatibility + wait/notify
├── lock/LockManager.java           # lock lifecycle per transaction
├── transaction/TransactionContext.java
├── transaction/TransactionManager.java
├── trace/TransactionTrace.java     # timeline quan sát được
├── SharedReadersDemo.java
├── NonRepeatableDemo.java
├── WriterContentionDemo.java
├── RollbackReleasesLockDemo.java
└── LockUpgradeDemo.java
```

`TransactionTrace` là helper cho demo, không phải database feature production.
Nó đánh số event phát sinh từ transaction manager và lock manager, nên output
cho thấy request, wait, grant, read/write, commit/rollback và release theo đúng
thứ tự thực tế giữa các Java threads.

## Các demo

Mọi demo dùng `CountDownLatch`, không dùng `Thread.sleep()`. Timeline vì thế
deterministic: một test chỉ cho transaction tiếp tục khi trạng thái cần quan sát
đã thực sự xảy ra.

| Demo | Điều được chứng minh |
| --- | --- |
| `SharedReadersDemo` | `S + S` compatible: hai reader cùng giữ lock, không có `WAIT` |
| `NonRepeatableDemo` | writer phải wait cho reader commit; T1 đọc lại vẫn thấy `1000` |
| `WriterContentionDemo` | `X + X` conflict: writer thứ hai chạy sau commit của writer đầu |
| `RollbackReleasesLockDemo` | rollback restore before-image, rồi release lock cho writer đang chờ |
| `LockUpgradeDemo` | sole reader upgrade `S → X` rồi update được row |

### 1. Shared readers: concurrency được giữ lại

```text
01 T1 REQUEST S(1)
02 T1 GRANTED S(1)
03 T2 REQUEST S(1)
04 T2 GRANTED S(1)
05 SYS Both readers hold S(1) concurrently — no WAIT event.
```

T2 được grant dù T1 chưa commit. Nếu mọi read dùng `X` lock, T2 sẽ phải chờ
không cần thiết.

### 2. Non-repeatable read bị chặn

```text
T1: S(A), READ A = 1000
T2: REQUEST X(A) -> WAIT
T1: READ A = 1000
T1: COMMIT, RELEASE S(A)
T2: GRANTED X(A), UPDATE A = 500, COMMIT
```

Output quan trọng:

```text
07 T2 WAIT X(1) — held by S[(T1)]
10 T1 READ A1 = 1000
11 SYS T1 saw stable value across reads = true
14 T2 GRANTED X(1)
```

Đây là điểm khác biệt cốt lõi với STEP 5: T2 không thể chen update vào giữa hai
reads của T1.

### 3. Writer contention: lost update không còn là interleaving tự do

```text
T1: X(A), UPDATE A: 1000 -> 900
T2: REQUEST X(A) -> WAIT
T1: COMMIT, RELEASE X(A)
T2: GRANTED X(A), UPDATE A: 900 -> 850
```

T2 chỉ được write sau khi T1 kết thúc. Demo không biến mọi business update
thành đúng tự động, nhưng loại bỏ concurrent overwrite của hai writer trên cùng
row.

### 4. Rollback cũng phải release lock

```text
T1: X(A), UPDATE A: 1000 -> 900
T2: REQUEST X(A) -> WAIT
T1: ROLLBACK, RESTORE A = 1000, RELEASE X(A)
T2: GRANTED X(A), UPDATE A: 1000 -> 800
```

Thứ tự `restore` trước `release` quan trọng: T2 không thể chạm vào row ở state
`900` chưa commit của T1.

### 5. Lock upgrade

```text
T1: REQUEST S(A) -> GRANTED
T1: READ A = 1000
T1: REQUEST X(A) — upgrade S -> X
T1: GRANTED X(A)
T1: UPDATE A: 1000 -> 900
```

Upgrade là safe khi T1 là reader duy nhất. Hai reader cùng xin upgrade có thể
chờ lẫn nhau; deadlock detection chưa có ở STEP 6 và là nội dung của STEP 8.

## Chạy STEP 6

Từ repository root:

```bash
javac -d /tmp/db-from-scratch-classes $(rg --files -g '*.java')

java -cp /tmp/db-from-scratch-classes dbformscratch.step6.SharedReadersDemo
java -cp /tmp/db-from-scratch-classes dbformscratch.step6.NonRepeatableDemo
java -cp /tmp/db-from-scratch-classes dbformscratch.step6.WriterContentionDemo
java -cp /tmp/db-from-scratch-classes dbformscratch.step6.RollbackReleasesLockDemo
java -cp /tmp/db-from-scratch-classes dbformscratch.step6.LockUpgradeDemo
```

Tìm các event sau trong output:

```text
REQUEST  -> transaction xin lock
WAIT     -> request conflict với holder hiện tại
GRANTED  -> lock đã thuộc transaction
RELEASE  -> chỉ xuất hiện sau COMMIT hoặc ROLLBACK
```

## STEP 6 giải quyết gì, chưa giải quyết gì?

| Có | Chưa có |
| --- | --- |
| stable read cho existing row đang giữ `S` | predicate/range lock, nên chưa chặn phantom read |
| one writer at a time cho một row | deadlock detection, timeout, victim selection |
| không đọc/write qua row đang bị `X` lock | fairness; writer có thể bị reader mới chen vào |
| rollback restore rồi mới unlock | MVCC/snapshot read để reader không cần chờ writer |
| lock lifecycle theo transaction | durable commit, WAL và recovery |

Đây là row locking tối giản, không phải implementation của Oracle hay InnoDB.
Ví dụ, production engine có lock table, wait queue, timeout, cancellation,
deadlock detector, isolation-level policy và nhiều tối ưu khác.

## Map sang SQL và production

Conceptually:

```java
database.select(transaction, 1L);       // requests S(ACCOUNT:1)
database.update(transaction, account);  // requests X(ACCOUNT:1)
```

Tương tự một lock-based database giữ lock trong transaction:

```sql
BEGIN;
SELECT * FROM account WHERE id = 1;
UPDATE account SET balance = 500 WHERE id = 1;
COMMIT;
```

Tuy vậy, không nên suy diễn rằng mọi Oracle/PostgreSQL/MySQL `SELECT` thường
lấy shared row lock đúng như model này. Engine thật dùng isolation policy và,
nhiều trường hợp, MVCC. STEP 6 cố ý dùng rigorous 2PL vì nó làm visibility và
contention quan sát được; STEP 7 sẽ phát minh version/snapshot để giải quyết
trade-off reader phải chờ writer.

## Checkpoint

```text
STEP 5
    concurrent transactions can read or overwrite unsafe state

STEP 6
    locks protect transaction assumptions until transaction end
    S keeps readers concurrent; X serializes conflicting access

STEP 7
    readers should not always wait for writers
    -> versioning, snapshots, MVCC
```

> A lock does not merely protect one statement. In this rigorous 2PL model, it protects the
> transaction's assumptions until that transaction commits or rolls back.
