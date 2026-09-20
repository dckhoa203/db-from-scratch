# STEP 1 — MiniDatabase: Mutable State Manager

## Mục tiêu đã hoàn thành

STEP 1 xây phiên bản nhỏ nhất có thể của database: một object quản lý state
account trong RAM.

```text
Application
    │
    ▼
MiniDatabase
    │
    ▼
HashMap<Long, Account>
    │
    ▼
RAM
```

Mental model cần giữ là:

```text
Database = Mutable State Manager
```

Ở level này, state là các account:

```text
Account #1 = 1000
Account #2 = 500
Account #3 = 800
```

`MiniDatabase` đã cung cấp bốn state transition/read cơ bản:

| Operation | Code hiện tại | Kết quả |
| --- | --- | --- |
| INSERT | `accounts.put(account.id(), account)` | đặt account vào state |
| SELECT | `accounts.get(id)` | đọc account theo ID |
| UPDATE | `accounts.put(account.id(), account)` | thay account bằng state mới |
| DELETE | `accounts.remove(id)` | loại account khỏi state |

`Account` dùng Java `record`, nên immutable. Update tạo một `Account` mới với
balance mới, rồi thay value tại cùng key. Điều này giúp nhìn rõ luồng
`read → calculate new state → write`.

## Đã làm được gì?

- Quản lý `Account` theo `id` trong RAM.
- Thực hiện được đầy đủ `INSERT`, `SELECT`, `UPDATE`, `DELETE`.
- Minh hoạ được state thay đổi từ `balance = 1000` thành `balance = 900`.
- Xoá account và nhận lại `null` khi đọc ID không còn tồn tại.
- Map được mô hình `Map<Long, Account>` sang khái niệm Oracle table `ACCOUNT`.

## Mapping sang Oracle

```text
Map<Long, Account>
        ↓
Oracle table ACCOUNT
```

```sql
CREATE TABLE ACCOUNT (
    ID              NUMBER PRIMARY KEY,
    ACCOUNT_NUMBER  VARCHAR2(50),
    BALANCE         NUMBER
);
```

| Java call | Oracle equivalent (conceptual) |
| --- | --- |
| `database.select(1L)` | `SELECT * FROM ACCOUNT WHERE ID = 1` |
| `database.update(account)` | `UPDATE ACCOUNT SET BALANCE = 900 WHERE ID = 1` |
| `database.delete(1L)` | `DELETE FROM ACCOUNT WHERE ID = 1` |

Đây là mapping về ý nghĩa. `HashMap` không phải database engine hoặc Oracle
storage engine.

## Thiếu sót có chủ đích

| Thiếu gì | Hệ quả hiện tại |
| --- | --- |
| Concurrent access control | Nhiều thread có thể cùng sửa một state mà không phối hợp |
| Atomic multi-step operation | `read → modify → write` không phải một operation duy nhất |
| Transaction và rollback | Không thể đảm bảo nhiều update cùng thành công hoặc cùng thất bại |
| Durability | Process dừng là toàn bộ `HashMap` biến mất |
| WAL và recovery | Không có cách khôi phục state sau crash |
| Page, buffer pool, index | Chưa có storage engine hay cách truy cập data ở disk scale |
| SQL, connection, query engine | Application đang gọi Java method trực tiếp |

Ngoài ra, `insert` và `update` hiện cùng gọi `put`. Vì vậy duplicate `insert`
sẽ overwrite row cũ, chưa enforce primary-key constraint như Oracle.

## STEP 2 sẽ giải quyết gì?

STEP 2 tập trung vào thiếu sót đầu tiên: nhiều actor cùng modify mutable state.
Ta thêm operation `transfer(fromId, toId, amount)`:

```java
public void transfer(long fromId, long toId, long amount) {
    Account from = select(fromId);
    Account to = select(toId);

    update(new Account(from.id(), from.accountNumber(), from.balance() - amount));
    update(new Account(to.id(), to.accountNumber(), to.balance() + amount));
}
```

Với một thread và `A = 1000`, `B = 1000`, transfer `100` từ A sang B sẽ giữ
invariant `A + B = 2000`.

```text
A = 1000, B = 1000
        │ transfer 100
        ▼
A =  900, B = 1100
TOTAL = 2000
```

Khi nhiều thread gọi `transfer()` đồng thời, mỗi thread có thể đọc state cũ rồi
ghi đè kết quả của thread khác. STEP 2 chủ động tái hiện **race condition** và
**lost update** để chứng minh `read → modify → write` không atomic. Bước row
lock tiếp theo sẽ bảo vệ đoạn thao tác này.

## Checkpoint

Sau STEP 1, đừng chỉ nhớ database là phần mềm lưu data. Hãy hỏi:

```text
What state exists?
Who owns it?
Who can read it?
Who can modify it?
What happens when multiple actors modify it at the same time?
```

> A database fundamentally manages mutable state. A map is enough to start.
> Concurrency, failure, persistence, and efficient access are the pressures
> that force the mechanisms of a real database.
