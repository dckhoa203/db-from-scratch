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
| `ConcurrentDepositDemo` | Stress test 100 deposits đồng thời | Có thể ra balance nhỏ hơn `2000` |
| `LostUpdateDemo` | Ép interleaving bằng `CyclicBarrier` | Luôn tái hiện `1200` expected, `1100` actual |
| `StressTransferDemo` | Dùng invariant tổng tiền cho transfer hai chiều | Chuẩn bị cho stress test multi-row |

`depositUnsafe()` đặt barrier **sau khi đọc account**. Vì vậy cả hai thread
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
```

Stress test đôi khi có thể tình cờ ra `2000`. Điều đó không chứng minh code
đúng; race condition phụ thuộc vào scheduling và interleaving tại thời điểm
chạy. `LostUpdateDemo` là bằng chứng chắc chắn hơn vì nó dựng đúng execution
gây lỗi.

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

## Transfer invariant

Với `A = 1000` và `B = 1000`, transfer chỉ được di chuyển tiền:

```text
A + B = 2000 trước transfer
A + B = 2000 sau transfer
```

Invariant quan trọng hơn việc method không ném exception. Một backend có thể
trả HTTP 200 nhưng vẫn sai data nếu concurrent update phá `A + B = 2000`.

## Review note: StressTransferDemo

Ý tưởng dùng transfer và invariant tổng tiền rất đúng, nhưng demo hiện tại đọc
và in `A`, `B`, `Total` **ngay trong vòng lặp submit**, trước khi các task hoàn
thành. Những con số đó là intermediate observation, không phải final result;
ngay cả implementation đúng cũng có thể bị quan sát giữa hai update của một
transfer.

Để biến nó thành final-invariant test ở bước tiếp theo, cần:

```java
// submit all tasks
executor.shutdown();
executor.awaitTermination(10, TimeUnit.SECONDS);

// only then select A and B once, then check A.balance() + B.balance()
```

`awaitTermination` cũng giúp JVM không thoát trước khi task cuối hoàn thành.

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
