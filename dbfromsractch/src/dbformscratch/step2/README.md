# STEP 2 — Hai thread cùng UPDATE

## Mục tiêu

STEP 1 có một `Map<Long, Account>` quản lý mutable state trong RAM. STEP 2
thêm nhiều thread để trả lời câu hỏi mới:

```text
Database = Shared Mutable State Manager
```

Nếu hai actor cùng đọc, tính toán và ghi lại cùng một account, state cuối cùng
có còn đúng không?

## Implementation đã làm được

`MiniDatabase` chuyển từ `HashMap` sang `ConcurrentHashMap`:

```java
private final Map<Long, Account> accounts = new ConcurrentHashMap<>();
```

Đây là lựa chọn đúng cho experiment. Nó làm từng operation trên map (`get`,
`put`, `remove`) thread-safe, nhờ đó ta cô lập đúng vấn đề cần học:

```text
Problem A: HashMap concurrent modification
Problem B: read → modify → write is not atomic
```

STEP 2 tập trung vào Problem B. `ConcurrentHashMap` không biến ba lời gọi sau
thành một operation duy nhất:

```java
Account current = select(accountId);       // READ
long newBalance = current.balance() + amount; // MODIFY
update(new Account(..., newBalance));      // WRITE
```

### Demos hiện có

| Demo | Mục đích | Kết quả mong đợi |
| --- | --- | --- |
| `ConcurrentDepositDemo` | 100 deposits chạy trên 10 worker threads | Có thể ra balance nhỏ hơn `2000` |
| `LostUpdateDemo` | Ép interleaving bằng `CyclicBarrier` | Luôn tái hiện `1200` expected, `1100` actual |
| `StressTransferDemo` | Optional preview về invariant tổng tiền | Đọc final state sau khi mọi transfer hoàn tất |

`depositWithBarrier()` đặt barrier **sau khi đọc account**. Vì vậy cả hai thread
đều phải chụp `balance = 1000` trước khi được phép tính và ghi:

```text
T1: read 1000 ──────┐
                     ├── barrier opens
T2: read 1000 ──────┘

T1: write 1100
T2: write 1100

final: 1100
```

Đây là deterministic experiment: barrier không phải lock hay solution cho
database; nó chỉ điều khiển timeline để bug không phụ thuộc vào may mắn của
scheduler.

Trong stress test, cần phân biệt rõ:

```text
100 deposit operations
10 worker threads
```

Một worker có thể thực thi nhiều deposit; operation count không phải thread
count.

## Đã verify

Compile toàn bộ Java sources và chạy:

```bash
javac -d /tmp/db-from-scratch-step2-classes $(rg --files -g '*.java')
java -cp /tmp/db-from-scratch-step2-classes dbformscratch.step2.LostUpdateDemo
java -cp /tmp/db-from-scratch-step2-classes dbformscratch.step2.ConcurrentDepositDemo
```

Kết quả đã quan sát:

```text
LostUpdateDemo
Expected = 1200
Actual = 1100

ConcurrentDepositDemo
Expected balance = 2000
Actual balance = 1750..1880   // thay đổi theo lần chạy

StressTransferDemo
Expected total = 2000
Actual total = a different value
Invariant preserved = false
```

Các stress test đôi khi có thể tình cờ giữ đúng expected balance/invariant.
Điều đó không chứng minh code đúng; race condition phụ thuộc vào scheduling và
interleaving tại thời điểm chạy. `LostUpdateDemo` là bằng chứng chắc chắn hơn
vì nó dựng đúng execution gây lỗi.

## Lost update là gì?

Hai thread cùng dựa vào cùng old state `V0 = 1000`:

```text
V0 = 1000
 ├── T1: +100 → 1100
 └── T2: +100 → 1100
              │
              ▼
          final = 1100
```

Một update đã ghi đè update kia. Không có exception, không có crash, nhưng
business result sai.

Điểm cần nhớ:

```text
Thread-safe individual operations
≠
Thread-safe business operation
```

Ta muốn `READ → MODIFY → WRITE` xuất hiện với thread khác như một operation
không thể chia nhỏ. Kết quả chỉ cần tương đương một trong hai serial execution:

```text
T1 hoàn tất rồi T2  → 1200
T2 hoàn tất rồi T1  → 1200
```

Khái niệm “tương đương serial execution” này là cầu nối tới serializability ở
những step sau.

## Optional preview: transfer invariant

Với `A = 1000` và `B = 1000`, transfer chỉ được di chuyển tiền:

```text
A + B = 2000 trước transfer
A + B = 2000 sau transfer
```

Invariant quan trọng hơn việc method không ném exception. Một backend có thể
trả HTTP 200 nhưng vẫn sai data nếu concurrent update phá `A + B = 2000`.

`StressTransferDemo` submit toàn bộ operations, đóng executor, chờ mọi task
hoàn tất rồi mới đọc final state:

```java
executor.shutdown();
executor.awaitTermination(10, TimeUnit.SECONDS);
// select A and B once, then check A.balance() + B.balance()
```

Transfer không phải bằng chứng chính của STEP 2 vì nó đưa thêm multi-row
atomicity vào bài toán. Nó chỉ là preview cho business invariant và transaction
ở các step sau; single-row deposit vẫn là experiment cô lập lost update tốt
nhất.

## Chưa giải quyết ở STEP 2

Không thêm `synchronized`, lock, transaction hay atomic update ở đây. Một
`synchronized` trên cả `MiniDatabase` có thể làm demo đúng, nhưng sẽ block cả
database: update Account #1 và Account #999999 cũng phải chờ nhau.

Đó là correctness đổi lấy concurrency quá thô. STEP 3 sẽ phát minh **row-level
lock** để chỉ các writer đụng cùng account mới phải phối hợp.

## Map sang Oracle/backend

Application code kiểu sau có cùng cửa sổ race:

```sql
SELECT balance FROM account WHERE id = 1;
-- Java tính balance mới
UPDATE account SET balance = :newBalance WHERE id = 1;
```

`UPDATE account SET balance = balance + :amount WHERE id = 1` thu hẹp bài toán
cho một counter trên một row, nhưng không thay thế transaction cho transfer
multi-row. Database engine có concurrency control không đồng nghĩa application
sequence tự động đúng.

## Checkpoint

```text
Shared mutable state + concurrent writers
    ↓
read → modify → write can interleave
    ↓
lost update
    ↓
need coordination
    ↓
row lock (STEP 3)
```
