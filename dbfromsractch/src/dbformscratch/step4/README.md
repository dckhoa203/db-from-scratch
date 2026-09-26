# STEP 4 — Multi-row Transaction

## Problem: row lock vẫn chưa đủ

STEP 3 trả lời câu hỏi: ai được modify một row ngay bây giờ? Nhưng transfer
không chỉ thay đổi một row:

```text
A -= 100
B += 100
```

Với `A = 1000` và `B = 1000`, invariant là:

```text
A + B must remain 2000
```

Nếu debit A thành công rồi operation fail trước credit B:

```text
A = 900
B = 1000
TOTAL = 1900   ❌
```

Mỗi `UPDATE` riêng lẻ có thể đúng, nhưng business operation `TRANSFER` vẫn sai.
Ta cần nói với database rằng hai writes này **belong together**.

## Mental model

```text
Transaction
    ├── UPDATE A
    └── UPDATE B

success → COMMIT
failure → ROLLBACK
```

> A transaction is a group of state changes that appear all-or-nothing.

Đây là Atomicity: sau khi transaction kết thúc, business chỉ được thấy either
transfer hoàn tất (`900 / 1100`) hoặc transfer chưa từng xảy ra (`1000 / 1000`).
Không được thấy partial state `900 / 1000`.

## Implementation

### TransactionContext

Mỗi transaction có:

```text
transactionId
state: ACTIVE | COMMITTED | ROLLED_BACK
beforeImages
writeSet
trace
```

`beforeImages` giữ value trước write đầu tiên của một row. `putIfAbsent()` là
chi tiết quan trọng: nếu transaction update A hai lần, rollback vẫn phải restore
value từ **trước lần update đầu tiên**, không phải value giữa transaction.

```text
Initial A = 1000
UPDATE A = 900
UPDATE A = 850

BeforeImage[A] = 1000
```

`writeSet` trả lời transaction đã modify row nào; `beforeImages` trả lời rollback
phải restore giá trị nào.

### Transactional update

Mọi write trong transfer phải đi qua overload có transaction:

```java
database.update(transaction, updatedFrom);
database.update(transaction, updatedTo);
```

Method này lần lượt:

```text
ensure ACTIVE
capture BEFORE_IMAGE once
record WRITE_SET once
apply UPDATE to current in-memory state
append trace event
```

`MiniDatabase` chỉ expose transactional update trong STEP 4, nên transfer không
thể vô tình bypass before-image tracking. Đây là lý do cả debit lẫn credit đều
phải transactional.

### Lifecycle

```text
BEGIN TX-1
    UPDATE A
    UPDATE B
COMMIT TX-1
```

Hoặc:

```text
BEGIN TX-1
    UPDATE A
    FAILURE
ROLLBACK TX-1
    RESTORE A from before image
```

`COMMIT` hiện chỉ mark transaction là `COMMITTED`, vì writes đã nằm trong RAM.
Khi track có WAL, disk và `fsync`, commit mới cần durable protocol phức tạp hơn.

## Experiments

### Success path

`AtomicityTransactionSuccessDemo` transfer `100` từ A sang B:

```text
BEGIN TX-1
TX-1 BEFORE_IMAGE account=1 balance=1000
TX-1 WRITE_SET += account=1
TX-1 UPDATE account=1: 1000 -> 900
TX-1 BEFORE_IMAGE account=2 balance=1000
TX-1 WRITE_SET += account=2
TX-1 UPDATE account=2: 1000 -> 1100
COMMIT TX-1
TX-1 STATE = COMMITTED

Final: A=900, B=1100, Total=2000
Invariant preserved = true
```

### Failure path: debit rồi rollback

`AtomicityTransactionFailDemo` cố tình fail **sau** debit A. Exception được
catch trong demo để state cuối luôn được in:

```text
BEGIN TX-1
TX-1 BEFORE_IMAGE account=1 balance=1000
TX-1 WRITE_SET += account=1
TX-1 UPDATE account=1: 1000 -> 900
TX-1 FAILURE: Failure after debit
ROLLBACK TX-1
TX-1 RESTORE account=1: 900 -> 1000
TX-1 STATE = ROLLED_BACK

Final: A=1000, B=1000, Total=2000
Invariant preserved = true
```

Đây là bằng chứng quan trọng nhất của STEP 4: state `A=900, B=1000` có xuất
hiện trong transaction, nhưng không tồn tại sau khi transaction abort.

## Chạy STEP 4

Từ repository root:

```bash
javac -d /tmp/db-from-scratch-classes $(rg --files -g '*.java')

java -cp /tmp/db-from-scratch-classes \
  dbformscratch.step4.AtomicityTransactionSuccessDemo

java -cp /tmp/db-from-scratch-classes \
  dbformscratch.step4.AtomicityTransactionFailDemo
```

## Scope có chủ đích

### Đây là exception rollback, chưa phải machine-crash recovery

STEP 4 xử lý Java exception khi JVM vẫn sống để code có thể gọi `rollback()`.
Nếu process chết sau `UPDATE A`, before images chỉ ở RAM cũng biến mất. Durability,
WAL và recovery là problem riêng ở STEP 9–11.

### Atomicity không phải isolation

Writes hiện được áp dụng trực tiếp vào map trước commit. Một transaction khác có
thể đọc value chưa commit, rồi transaction đầu rollback. Đó là dirty read và là
starting point của STEP 5.

### Chưa kết hợp transaction lock lifetime

STEP 4 cố tình không mang Row Lock từ STEP 3 vào transaction. Lock A và B từ
begin đến commit dẫn tới multi-row lock ordering, waiting và deadlock—theo plan,
đó là Two-Phase Locking ở STEP 6.

### Update-only mini transaction

Rollback hiện restore before images của existing accounts. Transactional
insert/delete và nested transaction chưa thuộc scope step này.

## Map sang JDBC / Oracle

```java
connection.setAutoCommit(false);
try {
    debitAccount();
    creditAccount();
    connection.commit();
} catch (Exception exception) {
    connection.rollback();
    throw exception;
}
```

Transaction boundary được xác định bởi business invariant, không chỉ vì có nhiều
SQL statements. Với banking, debit balance, credit balance và transaction record
có thể phải cùng thuộc một transaction; external API call hoặc email thì không
thể đơn giản nhét vào local database transaction.

## Checkpoint

```text
STEP 3: Who may modify this row now?
        → Row lock

STEP 4: Which changes must succeed or fail together?
        → Transaction + before image + rollback

STEP 5: What may another transaction see before commit?
        → Isolation problems
```

> Atomicity protects a business operation from partial updates. In a transfer,
> debit and credit must either both commit or both be rolled back.
