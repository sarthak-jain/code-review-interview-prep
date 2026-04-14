# Airbnb Senior SWE — Code Review Interview Guide
*Read this before every practice session*

---

## What Is This Round?

Airbnb introduced the **Code Review interview** in 2024 to replace the second coding round for senior+ engineers. The logic: senior engineers spend a large portion of their day reviewing code, not writing it from scratch. This round tests **exactly that skill**.

- **Duration:** 60 minutes
- **Format:** You receive a pre-written code sample (a small app, algorithm, or system component). Your job is to review it like a teammate's PR.
- **Language:** Java (or your chosen language). Expect 150–400 lines.
- **What they give you:** Realistic-looking code with intentional bugs, design flaws, security issues, and style problems embedded throughout.
- **What you do:** Identify issues, explain *why* they're problems, and propose fixes — out loud, collaboratively.

---

## What Interviewers Are Evaluating

| Signal | What They Want to See |
|---|---|
| **Depth of finding** | Do you catch subtle bugs, not just typos? |
| **Breadth of coverage** | Do you cover correctness, performance, security, design — not just one area? |
| **Prioritization** | Can you distinguish a P0 crash bug from a P3 style nit? |
| **Communication** | Do you explain *why* it's a problem and *how* to fix it — like a senior mentor would? |
| **Senior mindset** | Do you think about the system beyond the code snippet? Scalability, operability, team norms? |

---

## The 5-Category Framework (Your Mental Checklist)

Use this mental model on every review. Go through the code at least **twice** — once for correctness/bugs, once for design/quality.

### 🔴 Category 1: Correctness & Bugs (Highest Priority)
*These cause crashes or wrong behavior in production.*

- **Logic errors:** Off-by-one, wrong comparisons (`>` vs `>=`), inverted conditionals
- **Null pointer / NullPointerException:** Unchecked nulls, missing null guards
- **Race conditions / concurrency bugs:** Non-atomic operations on shared state, missing synchronization, incorrect use of collections in multi-threaded contexts
- **Integer overflow:** Arithmetic on `int` that should be `long`, unchecked multiplication
- **Wrong data types:** Using `float`/`double` for money (should be `BigDecimal`), lossy conversions
- **Edge cases not handled:** Empty list, single element, negative numbers, zero, max/min values
- **Incorrect algorithm:** Wrong loop bounds, incorrect base case in recursion
- **State mutation bugs:** Mutating a collection while iterating it (`ConcurrentModificationException`)

**Score weight: 10–15 points each**

---

### 🟠 Category 2: Security Vulnerabilities
*These get companies hacked or cause data breaches.*

- **SQL Injection:** String concatenation into SQL queries (should use PreparedStatement)
- **Unvalidated input:** No bounds checking, no sanitization on user-supplied data
- **Sensitive data exposure:** Logging passwords, tokens, PII; exposing stack traces to clients
- **Hardcoded secrets:** API keys, passwords, tokens in source code
- **Improper authorization:** Missing ownership checks (can user A access user B's data?)
- **Insecure deserialization:** Trusting user-supplied serialized objects
- **Mass assignment / over-posting:** Setting fields from request that shouldn't be user-editable
- **Missing rate limiting / DOS vulnerability:** Loops or DB calls triggered by user input with no cap

**Score weight: 10–15 points each**

---

### 🟡 Category 3: Performance & Scalability
*These cause slowness, outages, or cost explosions at scale.*

- **N+1 query problem:** DB call inside a loop
- **Missing pagination:** Fetching unbounded result sets (`SELECT * FROM orders`)
- **Inefficient algorithm:** O(n²) where O(n log n) is achievable, linear scan of a sorted list
- **Unnecessary object creation:** Creating objects in tight loops, heavy objects in hot paths
- **Missing caching:** Repeated expensive computations or DB calls for same data
- **Blocking I/O on main thread / event loop**
- **Large memory allocations:** Loading entire file/result-set into memory
- **Missing indexes implied by the code's query patterns**

**Score weight: 8–12 points each**

---

### 🔵 Category 4: Error Handling & Reliability
*These cause silent failures or unrecoverable states.*

- **Swallowed exceptions:** `catch (Exception e) {}` or catch-and-log-only for recoverable errors
- **Missing finally / try-with-resources:** Resource leaks (DB connections, file handles, streams)
- **Incorrect exception types:** Throwing `RuntimeException` where a checked exception belongs, or vice versa
- **No retry / circuit breaker for external calls**
- **Missing transaction boundaries:** Multiple DB writes that should be atomic
- **Missing idempotency:** Operations that could be retried and cause double-processing
- **Overly broad catch clauses:** Catching `Throwable` or `Error`
- **No meaningful error messages:** Generic "Something went wrong" with no context

**Score weight: 8–12 points each**

---

### 🟢 Category 5: Code Quality & Design
*These slow down the team and make the codebase fragile.*

- **God class / method too long:** Method doing 10 things; should be decomposed
- **Violation of Single Responsibility Principle (SRP)**
- **Tight coupling:** Business logic in controllers/views; no separation of concerns
- **Magic numbers/strings:** `if (status == 3)` — what is 3?
- **Poor naming:** `int x`, `void doStuff()`, `String s2`
- **Code duplication (DRY violation):** Same logic copy-pasted in multiple places
- **Unnecessary complexity:** Overly clever one-liners, premature optimization
- **Wrong abstraction level:** Leaking implementation details through public APIs
- **Missing or wrong access modifiers:** Public fields that should be private
- **Mutable public state:** Public static mutable fields
- **Thread-unsafe Singleton patterns**
- **Missing or outdated comments on complex logic**
- **Test coverage gaps:** Critical paths with no tests (if tests are included in the PR)

**Score weight: 3–7 points each**

---

## The Review Strategy: How to Spend 60 Minutes

### Phase 1 — Understand the Code (5–8 min)
**Don't start commenting immediately.** First:
1. Read the class/file names, method signatures, and imports
2. Understand what this code is *supposed to do* (state it out loud: "This looks like a payment processing service that...")
3. Identify the main data flow (inputs → processing → outputs)
4. Note the domain context (booking, payments, search, etc.)

> **Say to interviewer:** "Let me take a couple minutes to understand the code's intent before I start reviewing."

### Phase 2 — First Pass: Correctness & Critical Issues (15–20 min)
Read line-by-line looking for bugs. Focus on:
- Logic errors and off-by-ones
- Null safety
- Concurrency
- Security (SQL injection, auth checks)
- Exception handling

Write notes as you go. Don't worry about style yet.

### Phase 3 — Second Pass: Design & Quality (10–15 min)
Zoom out and look at the structure:
- Is this class doing too much?
- Are there clear abstraction layers?
- What would be hard to test?
- What would break if requirements changed slightly?

### Phase 4 — Organize & Prioritize Your Findings (5 min)
Group your findings by severity:
- **Must fix** (crashes, security holes, data loss)
- **Should fix** (performance, reliability)
- **Nice to fix** (design, style)

### Phase 5 — Present Your Review (15–20 min)
Walk the interviewer through your findings. For each issue:
1. **Point to it** — "On line 47, in the `processPayment` method..."
2. **Name the problem** — "This is a potential NullPointerException"
3. **Explain the impact** — "If `booking` is null here, the entire request crashes with a 500"
4. **Propose a fix** — "We should add a null check or use `Optional<Booking>`"
5. **Rate the severity** — "This is a P0 — must fix before merge"

---

## Communication Templates (Say These Out Loud)

**For a bug:**
> "I see a potential issue on line X. The code does [Y], but if [Z condition], it will [bad outcome]. I'd recommend [fix] because [reason]."

**For a design issue:**
> "This method is doing a few different things — it's fetching data, applying business logic, and also formatting the response. I'd suggest extracting the business logic into a separate service class to keep this more testable and aligned with SRP."

**For a security issue:**
> "This is a security concern — the query is being built by string concatenation with user input, which opens us up to SQL injection. We should use a PreparedStatement with parameterized queries here."

**When you're not 100% sure:**
> "I want to flag this as a possible issue — I'd want to verify, but this looks like it could cause a race condition if multiple threads hit this simultaneously. I'd mark this as 'needs investigation' rather than a definite bug."

---

## Senior Engineer Mindset: What Separates Good from Great

| Junior Reviewer | Senior Reviewer |
|---|---|
| "This variable name is bad" | "This naming makes it hard to distinguish between booking IDs and listing IDs — at Airbnb scale that ambiguity causes bugs" |
| Spots obvious bugs | Spots subtle race conditions and idempotency issues |
| Reviews what's there | Asks "what's missing?" — no input validation, no observability |
| Flags everything equally | Clearly prioritizes P0 vs P3 |
| Says "this is wrong" | Says "this could cause [specific failure mode], here's how I'd fix it" |
| Reviews the code | Reviews the code *and* the design *and* asks about the test strategy |

---

## Key Things NOT to Do

- ❌ **Don't only look for style issues** — interviewers will be disappointed if you spend 30 min on formatting
- ❌ **Don't rush to comment without understanding the code first**
- ❌ **Don't be silent** — narrate your thought process even when scanning
- ❌ **Don't be harsh** — frame feedback as you would with a teammate ("I'd suggest..." not "This is terrible")
- ❌ **Don't only find one category of issues** — they expect breadth (bugs + security + design)
- ❌ **Don't skip proposing fixes** — just identifying problems isn't enough at senior level

---

## Airbnb-Specific Context

Airbnb's codebase is massive (booking, search, payments, messaging, host tools, etc.). Code review PRs in the interview are likely to resemble:

- **Payment/financial flows** (booking creation, refunds, pricing)
- **Search/recommendation services**
- **Data pipelines or background jobs**
- **API endpoints / REST controllers**
- **Concurrency-heavy services** (high-traffic booking flows)

**Airbnb's engineering values (reflect these in your review):**
- Reliability at scale (millions of bookings)
- Guest & host safety
- Data consistency (money must not be double-charged or lost)
- Clear, readable code for large teams
- Operational excellence (logging, metrics, error handling)

---

## Quick Pre-Review Checklist

Before you start your 60-minute review, mentally tick these off:

```
□ Do I understand what this code is supposed to do?
□ What are the main data flows / entry points?
□ What could go wrong with user input?
□ Are there any concurrent access patterns?
□ Are external resources (DB, APIs) handled correctly?
□ What happens on failure / exception?
□ Is sensitive data handled carefully?
□ Are there any obvious performance traps?
□ Does the design make sense? Is it testable?
□ What's MISSING that should be there?
```

---

*Good luck, Aditi! Remember: they're not just testing if you can find bugs — they're testing if you'd make their team better.*
