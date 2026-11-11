import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

fun main() {
    task1()
    task1WithThreadSleep() // optional part of Task 1
    task2()
    task3()
    task3WithJoin()        // optional part of Task 3
    task4()
    task5()
}

// ---------------------------------------------------------------- Task 1
fun task1() = runBlocking {
    println("\n=== Task 1: runBlocking + launch + delay ===")
    launch { delay(200); println("A done") }
    launch { delay(100); println("B done") }
    println("Start")
    // Expected: Start, B done, A done
}

// Optional: Thread.sleep instead of delay -> order changes
fun task1WithThreadSleep() = runBlocking {
    println("\n=== Task 1 (optional): Thread.sleep ===")
    launch { Thread.sleep(200); println("A done") }
    launch { Thread.sleep(100); println("B done") }
    println("Start")
    // Expected: Start, A done, B done
}

// ---------------------------------------------------------------- Task 2
private var fetchCalls = 0

suspend fun fetchName(): String? {
    delay(100)
    fetchCalls++
    return if (fetchCalls <= 2) null else "Ada"
}

suspend fun showUser(n: Int) {
    for (k in 1..n) {
        val name = fetchName()
        if (name != null) {
            println("User: $name")
            return
        }
        println("try $k failed")
    }
    println("Server is down, show cached user")
}

fun task2() = runBlocking {
    println("\n=== Task 2: suspend fun with n tries ===")
    println("-- n = 3 (recovery path) --")
    fetchCalls = 0
    showUser(3)
    println("-- n = 2 (failure path) --")
    fetchCalls = 0
    showUser(2)

    // Uncomment to see the compile error:
    // fun normalFun() { fetchName() }
}

// ---------------------------------------------------------------- Task 3
fun task3() = runBlocking {
    println("\n=== Task 3: Job + cancelAndJoin ===")
    val job = launch {
        try {
            repeat(5) { i ->
                println("tick $i")
                delay(100)
            }
        } finally {
            println("cleanup")
        }
    }
    delay(250)
    job.cancelAndJoin()
    println("isCancelled=${job.isCancelled}")
}

// Optional: join() instead of cancelAndJoin() -> all 5 ticks
fun task3WithJoin() = runBlocking {
    println("\n=== Task 3 (optional): join ===")
    val job = launch {
        try {
            repeat(5) { i ->
                println("tick $i")
                delay(100)
            }
        } finally {
            println("cleanup")
        }
    }
    job.join()
    println("isCompleted=${job.isCompleted}, isCancelled=${job.isCancelled}")
}

// ---------------------------------------------------------------- Task 4
suspend fun loadName(): String { delay(100); return "Ada" }
suspend fun loadAge(): Int { delay(100); return 36 }

fun task4() = runBlocking {
    println("\n=== Task 4: async ===")

    val sequential = measureTimeMillis {
        val name = loadName()
        val age = loadAge()
        println("sequential: $name, $age")
    }
    println("Sequential time: $sequential ms (~200)")

    val parallel = measureTimeMillis {
        val name = async { loadName() }
        val age = async { loadAge() }
        println("async + late await: ${name.await()}, ${age.await()}")
    }
    println("Async time: $parallel ms (~100)")

    val awaitEarly = measureTimeMillis {
        val name = async { loadName() }.await() // waits immediately
        val age = async { loadAge() }.await()
        println("async + early await: $name, $age")
    }
    println("Early await time: $awaitEarly ms (~200)")
}

// ---------------------------------------------------------------- Task 5
@OptIn(DelicateCoroutinesApi::class)
fun task5() = runBlocking {
    println("\n=== Task 5: dispatchers and threads ===")
    println("main() thread: ${Thread.currentThread().name}")

    launch(Dispatchers.Default) {
        println("Default: ${Thread.currentThread().name}")
    }.join()

    launch(Dispatchers.IO) {
        println("IO: ${Thread.currentThread().name}")
    }.join()

    launch(Dispatchers.Unconfined) {
        println("Unconfined (before delay): ${Thread.currentThread().name}")
        delay(50)
        println("Unconfined (after delay): ${Thread.currentThread().name}")
    }.join()

    val single = newSingleThreadContext("MyThread")
    launch(single) {
        println("MyThread before delay: ${Thread.currentThread().name}")
        delay(50)
        println("MyThread after delay: ${Thread.currentThread().name}")
    }.join()
    single.close() // must be closed, otherwise the thread is never released

    launch {
        println("No dispatcher (inherited): ${Thread.currentThread().name}")
    }.join()
}
