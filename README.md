# CoroutinesLab

Kotlin/JVM console project (kotlinx-coroutines-core 1.10.2). One function per task, all called from `main()`.

Run: `./gradlew run`  (or run `main()` in IntelliJ IDEA).

---

## Part B. Tasks and demos

> Each demo: max 40 seconds. Replace `VIDEO_URL_*` with your uploaded video link
> (drag an .mp4 into the GitHub README editor to get a URL).

<details>
<summary><b>Task 1 – runBlocking, launch, delay</b></summary>

Scenario:
1. Open `Main.kt`, show `task1()`.
2. Run, show output: `Start`, `B done`, `A done` (happy path).
3. *(optional)* Run `task1WithThreadSleep()`: order becomes `Start`, `A done`, `B done`.

**Why the order changes (optional):** `Thread.sleep()` blocks the only thread of `runBlocking`, so coroutine A (first launched) holds the thread for 200 ms before B can even start; `delay()` suspends and frees the thread, so B finishes first.

VIDEO_URL_1
</details>

<details>
<summary><b>Task 2 – suspend fun with n tries</b></summary>

Scenario:
1. Show `fetchName()` and `showUser(n)`.
2. Happy/recovery path, `n = 3`: `try 1 failed`, `try 2 failed`, `User: Ada`.
3. Failure path, `n = 2` (tries exhausted): `try 1 failed`, `try 2 failed`, `Server is down, show cached user`.
4. Temporarily add `fun normalFun() { fetchName() }` to the file, show the compile error, then remove it.

Compile error (calling suspend from a normal function):
```
Suspend function 'fetchName' should be called only from a coroutine or another suspend function.
```

VIDEO_URL_2
</details>

<details>
<summary><b>Task 3 – Job, cancelAndJoin</b></summary>

Scenario:
1. Show `task3()`.
2. Run: `tick 0`, `tick 1`, `tick 2`, `cleanup`, `isCancelled=true`.
3. *(optional)* Run `task3WithJoin()`: `tick 0..4`, `cleanup`, job completes (`isCompleted=true`).

VIDEO_URL_3
</details>

<details>
<summary><b>Task 4 – async</b></summary>

Scenario:
1. Run `task4()`.
2. Sequential: ~200 ms.
3. `async` + late `await()`: ~100 ms.
4. `await()` right after each `async`: ~200 ms again.

**Why ~200 ms again:** `await()` suspends the caller until that deferred is done, so the second `async` is not even started before the first one has finished. The work becomes sequential, `async` gives no benefit.

VIDEO_URL_4
</details>

<details>
<summary><b>Task 5 – dispatchers and threads</b></summary>

Scenario:
1. Run `task5()`.
2. `Default` → `DefaultDispatcher-worker-n`, `IO` → `DefaultDispatcher-worker-n`.
3. `Unconfined` → `main` before delay (after delay it resumes on the delay thread, `kotlinx.coroutines.DefaultExecutor`).
4. `newSingleThreadContext("MyThread")` → `MyThread` before and after `delay()`, then `close()`.
5. `launch` without dispatcher → inherited `main`.

VIDEO_URL_5
</details>

---

## How structured concurrency is handled in this repo

```kotlin
// Every task is wrapped in runBlocking { ... } -> it is the ROOT of a coroutine tree.
// Every launch/async inside is its CHILD and is bound to that parent Job.
//
// Task 1: runBlocking (parent) -> launch A, launch B (children).
//         runBlocking does not return until BOTH children complete, so "A done"
//         and "B done" can never be lost, even though println("Start") runs first.
//
// Task 3: job.cancelAndJoin() cancels the child; CancellationException is thrown
//         at the next suspension point (delay), the finally block runs ("cleanup"),
//         and join() waits until cleanup is finished. No leaked coroutine remains.
//
// Task 4: async children are started inside the parent's scope; if loadName() threw,
//         the exception would cancel the sibling loadAge() and the parent.
//
// Task 5: every .join() makes the parent wait for that child; newSingleThreadContext
//         is closed with close() in the same function, so the thread has the same
//         lifetime as the tree that uses it.
//
// There is no GlobalScope anywhere: nothing can outlive the function that started it.
```

---

## Part A. Theory

### Q1. `newSingleThreadContext` vs `Dispatchers.Unconfined`

`newSingleThreadContext("MyThread")` creates a dedicated thread. Every coroutine using it runs **only** on that thread: before and after `delay()` it is `MyThread`. Because it owns a real thread, you **must call `close()`** when finished (or use `.use {}`), otherwise the thread is never released. It is also marked `@DelicateCoroutinesApi`.

`Dispatchers.Unconfined` is not bound to any thread. The coroutine starts in the **caller's thread** (e.g. `main`), and after the first suspension it resumes in **whatever thread resumed it** (after `delay()` that is `kotlinx.coroutines.DefaultExecutor`). Nothing to close.

```kotlin
runBlocking {
    val ctx = newSingleThreadContext("MyThread")
    launch(ctx) {
        println("single before: ${Thread.currentThread().name}") // MyThread
        delay(50)
        println("single after:  ${Thread.currentThread().name}") // MyThread
    }.join()
    ctx.close()

    launch(Dispatchers.Unconfined) {
        println("unconfined before: ${Thread.currentThread().name}") // main
        delay(50)
        println("unconfined after:  ${Thread.currentThread().name}") // kotlinx.coroutines.DefaultExecutor
    }.join()
}
```

### Q2. `async` vs `launch`

| | `launch` | `async` |
|---|---|---|
| Returns | `Job` | `Deferred<T>` (a `Job` with a result) |
| Result | none (fire and forget) | `await()` returns the value |
| Waiting | `join()` waits for completion, returns `Unit` | `await()` waits **and** returns the result (or rethrows the exception) |

`join()` only waits; `await()` waits and gives the value. Exceptions in `launch` go to the parent immediately; in `async` they cancel the parent too but are also rethrown at `await()`.

### Q3. What is a coroutine? `suspend` vs `block`

A coroutine is a lightweight, suspendable computation. It is not tied to a thread: it can pause, free the thread, and later resume (possibly on another thread). Thousands of coroutines can run on a few threads.

- **Block**: the thread is held and does nothing (`Thread.sleep`).
- **Suspend**: the coroutine pauses and the thread is free for other work (`delay`).

With two coroutines on one thread:
```kotlin
runBlocking {                       // one thread
    val t1 = measureTimeMillis {
        coroutineScope {
            launch { delay(1000) }
            launch { delay(1000) }
        }
    } // ~1000 ms, they overlap
    val t2 = measureTimeMillis {
        coroutineScope {
            launch { Thread.sleep(1000) }
            launch { Thread.sleep(1000) }
        }
    } // ~2000 ms, the thread is blocked, they run one after another
}
```

### Q4. What does `suspend` do?

It marks a function that can **pause without blocking** a thread. It does **not** create a new thread. A suspend function can be called only from a coroutine or from another suspend function. The compiler adds a hidden last parameter `Continuation<T>` (continuation-passing style) and turns the function into a state machine; the return type becomes `Any?` (the result or the `COROUTINE_SUSPENDED` marker).

### Q5. Concurrency vs parallelism

- **Concurrency**: dealing with many tasks at once by interleaving them (progress on all, not necessarily simultaneous).
- **Parallelism**: tasks literally executing at the same moment.

Coroutines on **one thread** give **concurrency** (they switch at suspension points). **Parallelism** needs several threads on several CPU cores (e.g. `Dispatchers.Default`).

### Q6. Structured concurrency

Every coroutine lives in a scope and has a parent; coroutines form a tree of `Job`s.
- Parent **finishes** only after **all children** finish.
- A child **fails** → it cancels its parent, which cancels the other children (unless `SupervisorJob`/`supervisorScope` is used).
- Parent **cancelled** → all children are cancelled.

So there are no leaked coroutines, errors are not lost, and the lifetime is clear.

### Q7. Builders

| Builder | Returns | Blocks the thread? |
|---|---|---|
| `launch` | `Job` | No |
| `async` | `Deferred<T>` | No |
| `runBlocking` | `T` (result of block) | **Yes**, blocks the current thread until done |
| `withContext` | `T` | No (suspends; switches context) |
| `coroutineScope` | `T` | No (suspends until all children finish) |

Never use `runBlocking` on the Android UI thread: it blocks the main thread, freezes the UI, and can cause ANR (and deadlocks if the block needs the main thread).

### Q8. `CoroutineScope` and `CoroutineContext`

`CoroutineScope` defines **when** coroutines live (lifetime, via its `Job`; cancelling the scope cancels all of them). `CoroutineContext` is a set of elements (`Job`, `CoroutineDispatcher`, `CoroutineName`, `CoroutineExceptionHandler`); the dispatcher says **where** (on which thread) it runs.

If you specify nothing, the dispatcher is **inherited from the parent/scope**. If there is none (e.g. `GlobalScope.launch`), it is `Dispatchers.Default`. Inside `runBlocking` the coroutine runs on the calling thread (e.g. `main`).

### Q9. Default, IO, Main

| Dispatcher | Use for | Avoid |
|---|---|---|
| `Default` | CPU-heavy work: sorting a big list, parsing JSON, image processing | blocking calls (network, disk) |
| `IO` | network, files, database | long CPU-heavy computations |
| `Main` | updating UI, calling LiveData/State | any blocking or long work (freezes UI) |

### Q10. `viewModelScope.launch { withContext(Dispatchers.IO) { ... } }`

1. `viewModelScope` is a scope tied to the ViewModel; it uses `Dispatchers.Main.immediate` and a `SupervisorJob`.
2. `.launch { ... }` starts a new coroutine; its body starts on the **Main** thread.
3. `withContext(Dispatchers.IO) { ... }` suspends the coroutine, runs the block on an **IO thread** (DB/network), then returns the result.
4. After `withContext` returns, the coroutine resumes on the **Main** thread (code after it can touch the UI/state).

When the ViewModel is **cleared** (`onCleared()`), `viewModelScope` is cancelled, so the coroutine and its children are cancelled automatically (at the next suspension point, `finally` blocks run), which prevents leaks and updates to a dead screen.

---

## Submission checklist

- [ ] Private repo in TTPU organization, only the teacher invited
- [ ] Demo videos (≤ 40 s each) inserted in the `<details>` blocks above
- [ ] Answers to Q1–Q10 (above)
- [ ] Structured concurrency code block (above)
