# PR #1 Answer Key — DO NOT READ BEFORE REVIEWING

## Total Possible Score: 120 points (scaled to 100)

---

## 💡 REVIEW COMMENT FORMAT GUIDE

**Use these prefixes for clarity:**
- `🔴 BLOCKING` — Must fix before merge (crashes, data loss, security breach)
- `🟡 MAJOR` — Should fix before merge (serious bugs, performance issues, architectural flaws)
- `🔵 MINOR` — Nice to fix (style, small optimizations, code clarity)
- `💬 SUGGESTION` — Discuss/optional (alternative approaches, design discussion)

**File & Line References:**
Always cite the exact file and line number. Example: `BookingPaymentService.java:45`

**Tone:**
Collaborative, not accusatory. "I'd suggest..." not "You did this wrong."

---

## 🔴 CORRECTNESS & BUGS

### Issue 1 — Race Condition in processPayment (no transaction, double booking possible)

**Severity:** 🔴 BLOCKING

**Location:** `BookingPaymentService.java:44-65`

**GitHub Comment:**
```
🔴 BLOCKING: Race condition on concurrent bookings

Lines 44-65: The processPayment() method has a critical race condition. 
If two guests attempt to book the same listing simultaneously:

1. Both read the listing as available
2. Both charge their payment methods (lines 48-50)
3. Both insert booking records (line 52)
4. Both update the listing as unavailable (line 55)

Result: Two bookings confirmed for the same listing, both guests charged.

The charge operation (external call) and database updates are not atomic. 
We need to wrap this in a single database transaction:

```java
try {
  conn.setAutoCommit(false);
  
  // Select for update to lock the listing
  String lockQuery = "SELECT * FROM listings WHERE listing_id = ? FOR UPDATE";
  PreparedStatement lockStmt = conn.prepareStatement(lockQuery);
  lockStmt.setString(1, listingId);
  ResultSet lockRs = lockStmt.executeQuery();
  
  if (!lockRs.next() || lockRs.getBoolean("available") == false) {
    conn.rollback();
    return false;
  }
  
  // Charge card
  boolean charged = chargeCard(cardToken, totalAmount);
  
  if (charged) {
    // Insert booking and update listing in same transaction
    // ... (insert and update)
    conn.commit();
  } else {
    conn.rollback();
  }
} catch (Exception e) {
  conn.rollback();
  throw e;
}
```

Without this, we risk double-booking and financial loss.
```

**Score: 15 points**

---

### Issue 2 — refundBooking: rs.next() not checked

**Severity:** 🔴 BLOCKING

**Location:** `BookingPaymentService.java:82`

**GitHub Comment:**
```
🔴 BLOCKING: Unchecked ResultSet.next() leads to exception

Line 82: The code calls rs.next() without checking the return value:

```java
ResultSet rs = stmt.executeQuery(query);
rs.next();  // <-- What if this returns false?
String guestId = rs.getString("guest_id");  // SQLException here
```

If the bookingId doesn't exist, rs.next() returns false, but the code 
continues to line 83 and calls rs.getString(), which throws a 
SQLException. This exception is then swallowed by the catch block 
(line 90), leaving the refund in an inconsistent state.

Fix: Add a null check:

```java
if (!rs.next()) {
  throw new BookingNotFoundException("Booking not found: " + bookingId);
}
```

This way, non-existent bookings fail clearly instead of silently.
```

**Score: 10 points**

---

### Issue 3 — Wrong return type for revenue (int overflow / precision loss)

**Severity:** 🔴 BLOCKING

**Location:** `BookingPaymentService.java:107-108`

**GitHub Comment:**
```
🔴 BLOCKING: Integer overflow for revenue calculations + precision loss

Lines 107-108:
```java
int totalRevenue = 0;
// ...
totalRevenue += rs.getInt("total_amount");  // <-- Two problems here
```

**Problem 1 — Precision Loss:**
total_amount is stored as DOUBLE in the database (dollars.cents), but 
we're reading it as getInt(), which truncates the cents. A $100.50 
booking becomes $100, and at scale (thousands of bookings) this causes 
financial reporting errors.

**Problem 2 — Integer Overflow:**
int maxValue is ~2.1 billion. A single high-volume listing could easily 
exceed this. Example: A luxury NYC listing with 10 bookings/month at 
$500/night for 3 nights = $15k/month, $180k/year. Over 12 years, 
$2.16B — overflow happens.

Fix: Use BigDecimal for all monetary values:

```java
BigDecimal totalRevenue = BigDecimal.ZERO;
// ...
totalRevenue = totalRevenue.add(rs.getBigDecimal("total_amount"));
return totalRevenue;
```

And change the method signature: `public BigDecimal calculateListingRevenue(...)`
```

**Score: 10 points**

---

### Issue 4 — double used for money (pricePerNight, totalAmount)

**Severity:** 🔴 BLOCKING

**Location:** `BookingPaymentService.java:34, PaymentController.java:20`

**GitHub Comment:**
```
🔴 BLOCKING: Floating-point arithmetic for financial calculations

Lines 34, 52, 72: The code uses double for monetary values:

```java
public boolean processPayment(String guestId, String listingId, 
                               int nights, double pricePerNight) {
  double totalAmount = nights * pricePerNight;  // <-- WRONG
  
  // Later, stored in DB as double
  String insertQuery = "INSERT INTO bookings ... VALUES ... " + totalAmount;
}
```

IEEE 754 floating-point is not precise for money. Classic example:
```
0.1 + 0.2 = 0.30000000000000004  (not 0.3)
```

At Airbnb scale (millions of bookings), these rounding errors accumulate. 
Hosts lose fractions of cents, or guests are overcharged. This violates 
Airbnb's financial integrity.

Fix: Use BigDecimal throughout:

```java
public boolean processPayment(String guestId, String listingId, 
                               int nights, String pricePerNightStr) {
  BigDecimal pricePerNight = new BigDecimal(pricePerNightStr);
  BigDecimal totalAmount = pricePerNight.multiply(new BigDecimal(nights));
  // ... use totalAmount as BigDecimal everywhere
}
```

Accept price as a String from the API and parse it immediately to 
BigDecimal. Never use double for financial calculations.
```

**Score: 10 points**

---

### Issue 5 — currentStatus field fetched but never checked in refundBooking

**Severity:** 🔴 BLOCKING

**Location:** `BookingPaymentService.java:85`

**GitHub Comment:**
```
🔴 BLOCKING: Double-refund vulnerability (financial correctness)

Line 85:
```java
String currentStatus = rs.getString("status");
// ... but currentStatus is NEVER used

// Later, blindly issue refund regardless of state
refundCard(cardToken, amount);  // Line 88
```

If a booking is already REFUNDED, CANCELLED, or COMPLETED, the code 
still issues another refund. This is a classic double-refund bug.

Scenario:
1. Guest requests refund → status = REFUNDED, card is refunded $300
2. Same guest maliciously calls refund endpoint again → status is 
   already REFUNDED, but code issues ANOTHER $300 refund
3. Guest receives $600 instead of $300

Fix: Validate the current state before refunding:

```java
String currentStatus = rs.getString("status");

if (!"CONFIRMED".equals(currentStatus)) {
  throw new InvalidBookingStateException(
    "Cannot refund booking in " + currentStatus + " state. " +
    "Only CONFIRMED bookings can be refunded."
  );
}

// Now safe to refund
refundCard(cardToken, amount);
```

This ensures each booking is refunded exactly once.
```

**Score: 12 points**

---

### Issue 2 — refundBooking: rs.next() not checked
**Location:** `refundBooking()`, line `rs.next();`
**Problem:** The result of `rs.next()` is not checked. If `bookingId` doesn't exist, `rs.next()` returns false and subsequent `rs.getString()` calls will throw a `SQLException`, which is then silently swallowed (see Issue 5).
**Fix:** `if (!rs.next()) { throw new BookingNotFoundException(bookingId); }`
**Score: 10 points**

---

### Issue 3 — Wrong return type for revenue (int overflow / precision loss)
**Location:** `calculateListingRevenue()` — `int totalRevenue` and `rs.getInt("total_amount")`
**Problem 1:** `total_amount` is a `double` in the DB (it's a dollar amount), but it's read as `int`, truncating cents.
**Problem 2:** Revenue across many bookings could easily exceed `Integer.MAX_VALUE` (~$2.1B). Should be `long` or `BigDecimal`.
**Fix:** Use `BigDecimal` for all monetary values. Return `BigDecimal` from this method.
**Score: 10 points**

---

### Issue 4 — double used for money (pricePerNight, totalAmount)
**Location:** `processPayment()` params, `refundBooking()` amount field
**Problem:** Floating-point arithmetic is imprecise for money. `0.1 + 0.2 != 0.3` in IEEE 754. At scale, rounding errors accumulate and cause financial discrepancies.
**Fix:** Use `BigDecimal` throughout for all monetary calculations. Accept `String` from the API and construct `BigDecimal`.
**Score: 10 points**

---

### Issue 5 — currentStatus field fetched but never checked in refundBooking
**Location:** `refundBooking()` — `String currentStatus = rs.getString("status");`
**Problem:** The current status is read but never used. If a booking is already REFUNDED, CANCELLED, or COMPLETED, the code will blindly issue another refund — classic double-refund bug. This is a financial correctness issue.
**Fix:** Check `if (!"CONFIRMED".equals(currentStatus)) { throw new InvalidBookingStateException(...); }`
**Score: 12 points**

---

## 🟠 SECURITY VULNERABILITIES

### Issue 6 — SQL Injection (multiple locations)

**Severity:** 🔴 BLOCKING

**Location:** `BookingPaymentService.java:43, 52, 55, 72, 79, 104`

**GitHub Comment:**
```
🔴 BLOCKING: SQL Injection vulnerability in all queries

Lines 43, 52, 55, 72, 79, 104: Every single SQL query is built by 
concatenating user-supplied input directly into the SQL string:

```java
String query = "SELECT * FROM payment_methods WHERE guest_id = '" + 
               guestId + "'";  // <-- guestId is user input
```

An attacker can submit: `guestId = "' OR '1'='1"` to extract all 
payment methods for all guests:

```
SELECT * FROM payment_methods WHERE guest_id = '' OR '1'='1'
```

Worse: `guestId = "'; DROP TABLE bookings; --"` could destroy data.

This is a classic OWASP Top 10 vulnerability and can lead to complete 
database compromise.

**Fix:** Replace ALL instances of Statement with PreparedStatement:

```java
String query = "SELECT * FROM payment_methods WHERE guest_id = ?";
PreparedStatement pstmt = conn.prepareStatement(query);
pstmt.setString(1, guestId);  // Safe: parameter is escaped
ResultSet rs = pstmt.executeQuery();
```

Apply this fix to ALL queries in this file:
- Line 43: payment_methods lookup
- Line 52: booking insert
- Line 55: listing update  
- Line 72: booking lookup in refundBooking
- Line 79: booking update
- Line 104: guest bookings lookup
- Line 112: revenue query

This must be done across all services (PaymentService, ReviewService, etc).
```

**Score: 15 points**

---

### Issue 7 — Hardcoded DB credentials in source code

**Severity:** 🔴 BLOCKING

**Location:** `BookingPaymentService.java:18-20`

**GitHub Comment:**
```
🔴 BLOCKING: Hardcoded database credentials in source code

Lines 18-20:
```java
private static final String DB_URL = "jdbc:mysql://prod-db.airbnb.internal:3306/bookings";
private static final String DB_USER = "root";
private static final String DB_PASS = "Airbnb$ecure2024!";
```

Credentials in source code are exposed to:
- Anyone with repo access
- Git history (forever, even after deletion)
- Compiled JAR files shipped to production
- Reverse engineering of binaries
- Any developer who clones the repo

This grants anyone database access, allowing them to read all bookings, 
payment info, and modify/delete data.

**Fix:** Use environment variables or a secrets manager:

Option 1 — Environment variables:
```java
private static final String DB_URL = System.getenv("DB_URL");
private static final String DB_USER = System.getenv("DB_USER");
private static final String DB_PASS = System.getenv("DB_PASS");
```

Option 2 — AWS Secrets Manager (recommended for production):
```java
private static final String DB_PASS = getSecretFromSecretsManager("prod/db/password");
```

Option 3 — Spring's externalized config:
```java
@Value("${database.password}")
private String dbPassword;
```

Never commit credentials. Use a .env file (added to .gitignore) locally, 
and environment variables in CI/production.
```

**Score: 12 points**

---

### Issue 8 — Logging credit card number (PCI DSS violation)

**Severity:** 🔴 BLOCKING

**Location:** `BookingPaymentService.java:49`

**GitHub Comment:**
```
🔴 BLOCKING: Logging raw credit card number (PCI DSS violation)

Line 49:
```java
String cardNumber = rs.getString("card_number");
logger.info("Processing payment for guest " + guestId + " card: " + cardNumber);
```

Logging a raw card number violates:
- PCI DSS compliance (Airbnb could lose payment processor certification)
- Privacy laws (GDPR, CCPA)
- Airbnb's own security policies

Log files are:
- Often stored in centralized logging systems (shared across teams)
- Retained for months/years
- Less secure than the database
- Visible to anyone with log access

An attacker with log access can harvest card numbers.

**Fix:** Never log raw card numbers. Options:

Option 1 — Log only last 4 digits:
```java
String maskedCard = "****" + cardNumber.substring(cardNumber.length() - 4);
logger.info("Processing payment for guest " + guestId + " card: " + maskedCard);
```

Option 2 — Don't log the card at all:
```java
logger.info("Processing payment for guest: " + guestId);  // Remove card reference
```

Option 3 — Better: Don't SELECT card_number unless you need it:
```java
String query = "SELECT card_token FROM payment_methods WHERE guest_id = ?";
// Use card_token for charging, never log it
```

Also: Remove `card_number` from the SELECT on line 45 if it's not used 
elsewhere (defense in depth).
```

**Score: 12 points**

---

### Issue 9 — No authorization check in refundBooking and getGuestBookings

**Severity:** 🔴 BLOCKING

**Location:** `PaymentController.java:33, 44`

**GitHub Comment:**
```
🔴 BLOCKING: Missing authorization checks (any user can access any booking)

Lines 33, 44:
```java
@PostMapping("/refund/{bookingId}")
public String refundBooking(@PathVariable String bookingId) {
  paymentService.refundBooking(bookingId);  // <-- No auth check
  return "Refund processed";
}

@GetMapping("/bookings")
public List<String> getBookings(@RequestParam String guestId) {
  return paymentService.getGuestBookings(guestId);  // <-- No auth check
}
```

These endpoints have no authorization checks. Any user can:
1. Refund ANY booking by guessing a booking ID
2. Fetch booking history for ANY guest by passing their guestId

Exploit scenarios:
- Guest A calls `POST /api/payments/refund/guest-B-booking-id` → guest B 
  gets refunded without authorization
- Guest A calls `GET /api/payments/bookings?guestId=guest-B` → sees all 
  of guest B's bookings, addresses, payment amounts

This violates the principle of least privilege and basic access control.

**Fix:** Extract the authenticated user from the request and verify ownership:

```java
@PostMapping("/refund/{bookingId}")
public String refundBooking(@PathVariable String bookingId, 
                             @RequestHeader("Authorization") String token) {
  // Extract user from token (from SecurityContext)
  String currentUserId = getCurrentUserId();  // From Spring Security
  
  // Verify user is admin or the booking owner
  Booking booking = paymentService.getBooking(bookingId);
  if (!booking.getHostId().equals(currentUserId) && !isAdmin(currentUserId)) {
    throw new UnauthorizedException("You cannot refund this booking");
  }
  
  paymentService.refundBooking(bookingId);
  return "Refund processed";
}

@GetMapping("/bookings")
public List<String> getBookings() {
  String guestId = getCurrentUserId();  // Get from security context
  return paymentService.getGuestBookings(guestId);
}
```

Use Spring Security to inject the authenticated principal and verify 
access before calling service methods.
```

**Score: 12 points**

---

## 🟡 PERFORMANCE & SCALABILITY

### Issue 10 — New DB connection on every method call (no connection pooling)
**Location:** Every method — `DriverManager.getConnection(...)` called every time
**Problem:** Opening a raw JDBC connection is expensive (~100ms). At Airbnb's scale (millions of bookings/day), this exhausts DB connections and causes massive latency/failures.
**Fix:** Use a connection pool (HikariCP, c3p0) and inject a `DataSource`. In Spring, `@Autowired DataSource` handles this automatically.
**Score: 10 points**

---

### Issue 11 — getGuestBookings fetches ALL bookings with no pagination
**Location:** `getGuestBookings()` — unbounded `SELECT * FROM bookings`
**Problem:** A power user or host with thousands of bookings will pull the entire result set into memory. This causes OOM errors and DB timeouts.
**Fix:** Add LIMIT/OFFSET or cursor-based pagination. Caller should pass page/size params.
**Score: 8 points**

---

### Issue 12 — SELECT * instead of specific columns
**Location:** All queries using `SELECT *`
**Problem:** Fetches all columns (including large blobs, unnecessary fields) over the network. Wastes bandwidth, makes code fragile when schema changes, prevents index-only scans.
**Fix:** Select only the columns you need: `SELECT card_token FROM payment_methods WHERE ...`
**Score: 5 points**

---

## 🔵 ERROR HANDLING & RELIABILITY

### Issue 13 — refundBooking silently swallows all exceptions
**Location:** `refundBooking()` — `catch (Exception e) { // ignore errors }`
**Problem:** If the refund DB update fails after the card is refunded, we have an inconsistent state (money returned but booking still CONFIRMED). If chargeCard() throws, we'd never know. Silent failures are catastrophic in payment systems.
**Fix:** At minimum, log the exception. Better: use proper error handling, re-throw as a service exception, and set up alerting/dead-letter queues for payment failures.
**Score: 10 points**

---

### Issue 14 — DB connections and ResultSets never closed (resource leak)
**Location:** All methods — `Connection`, `Statement`, `ResultSet` never closed
**Problem:** Connection objects leak on every call. Under load, this exhausts the connection pool and causes the service to hang. ResultSet not closed can cause cursor exhaustion on some DBs.
**Fix:** Use try-with-resources: `try (Connection conn = ...; Statement stmt = ...; ResultSet rs = ...)`. Or use Spring's `JdbcTemplate` which handles this automatically.
**Score: 10 points**

---

### Issue 15 — processPayment has no transaction: charge can succeed but DB write fails
**Location:** `processPayment()` — no transaction wrapping charge + insert
**Problem:** `chargeCard()` succeeds (guest is charged), but then the INSERT or UPDATE throws (e.g., duplicate key). Guest is charged but has no booking. This is a financial data integrity bug.
**Fix:** Start a DB transaction before attempting DB writes. On failure, issue an automatic refund or use a saga/compensating transaction pattern.
**Score: 12 points** *(distinct from Issue 1 which is about concurrent bookings)*

---

## 🟢 CODE QUALITY & DESIGN

### Issue 16 — Unsafe Singleton (not thread-safe)
**Location:** `getInstance()` — classic double-checked locking missing
**Problem:** In a multi-threaded server environment (every Spring app), two threads can both see `instance == null` and create two instances simultaneously, leading to inconsistent state.
**Fix:** Use `synchronized` method, or `enum` singleton, or just let Spring manage it as a `@Service` bean (which is singleton-scoped by default).
**Score: 7 points**

---

### Issue 17 — Service class doing everything (SRP violation)
**Location:** `BookingPaymentService` — handles SQL, business logic, external payment calls, logging
**Problem:** Violates Single Responsibility Principle. This class is a DB access layer, business logic layer, AND external service client in one. Hard to test, hard to change, impossible to mock.
**Fix:** Separate into `BookingRepository` (DB access), `PaymentGatewayClient` (external calls), `BookingPaymentService` (orchestration). Use dependency injection.
**Score: 7 points**

---

### Issue 18 — PaymentController missing import for List
**Location:** `PaymentController.java` — `List<String>` used without import
**Problem:** `java.util.List` is not imported. Code won't compile.
**Fix:** Add `import java.util.List;`
**Score: 5 points**

---

### Issue 19 — processPayment returns boolean (insufficient for callers)
**Location:** Method signature `public boolean processPayment(...)`
**Problem:** A boolean doesn't tell the caller *why* it failed: Was the card declined? No payment method found? DB error? The controller just says "Payment failed. Please try again" — wrong message for "no payment method on file".
**Fix:** Return a `PaymentResult` object with a status enum (SUCCESS, CARD_DECLINED, NO_PAYMENT_METHOD, SYSTEM_ERROR) and an optional error message.
**Score: 6 points**

---

### Issue 20 — pricePerNight not validated in controller
**Location:** `PaymentController.bookListing()` — no validation on `pricePerNight`
**Problem:** A negative price or zero price would result in charging $0 or issuing a negative charge. Only `nights <= 0` is checked.
**Fix:** Also validate `pricePerNight > 0`. Consider validating `listingId` and `guestId` are non-null/non-empty.
**Score: 5 points**

---

### Issue 21 — Date/time: using java.util.Date (legacy)
**Location:** Import: `import java.util.Date;` (unused but present; also NOW() used instead of proper timezone-aware timestamp)
**Problem:** `java.util.Date` is legacy and has timezone issues. SQL `NOW()` uses DB server timezone which may differ from app server timezone. At global scale this causes booking time bugs.
**Fix:** Use `java.time.Instant` or `ZonedDateTime`. Pass timestamps from app layer, don't rely on DB `NOW()`.
**Score: 4 points**

---

## SCORING SUMMARY

| # | Issue | Category | Points |
|---|---|---|---|
| 1 | Race condition / no transaction on booking | Correctness | 15 |
| 2 | rs.next() result unchecked in refund | Correctness | 10 |
| 3 | int overflow / type mismatch for revenue | Correctness | 10 |
| 4 | double used for money (BigDecimal needed) | Correctness | 10 |
| 5 | currentStatus never checked → double refund | Correctness | 12 |
| 6 | SQL Injection (all queries) | Security | 15 |
| 7 | Hardcoded DB credentials | Security | 12 |
| 8 | Logging raw credit card number | Security | 12 |
| 9 | No authorization check | Security | 12 |
| 10 | No connection pooling | Performance | 10 |
| 11 | Unbounded query / no pagination | Performance | 8 |
| 12 | SELECT * | Performance | 5 |
| 13 | Swallowed exceptions in refund | Reliability | 10 |
| 14 | Resource leaks (no try-with-resources) | Reliability | 10 |
| 15 | No transaction: charge succeeds, DB write fails | Reliability | 12 |
| 16 | Unsafe singleton | Design | 7 |
| 17 | SRP violation / God service | Design | 7 |
| 18 | Missing import (won't compile) | Design | 5 |
| 19 | boolean return insufficient | Design | 6 |
| 20 | pricePerNight not validated | Design | 5 |
| 21 | Legacy Date / NOW() timezone issue | Design | 4 |
| **Total** | | | **196 raw → scaled to /100** |

**Scoring scale (out of 100):**
- 85–100: Exceptional — would hire senior
- 70–84: Strong — likely hire
- 55–69: Good — borderline, needs some coaching
- 40–54: Developing — would not hire senior yet
- <40: Needs significant work

**Raw score → /100 conversion:** divide raw score by 1.96 (e.g., raw 147 = ~75/100)
