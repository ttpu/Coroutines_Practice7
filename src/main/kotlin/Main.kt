import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

fun main() {
    task1()
    task1WithThreadSleep()
    task2()
    task3()
    task3WithJoin()
    task4()
    task5()
}

fun task1() = runBlocking {
    println("\n=== Task 1: runBlocking + launch + delay ===")
    launch { delay(200); println("A done") }
    launch { delay(100); println("B done") }
    println("Start")
}

fun task1WithThreadSleep() = runBlocking {
    println("\n=== Task 1 (optional): Thread.sleep ===")
    launch { Thread.sleep(200); println("A done") }
    launch { Thread.sleep(100); println("B done") }
    println("Start")
}

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
    println("-- n = 3 --")
    fetchCalls = 0
    showUser(3)
    println("-- n = 2 --")
    fetchCalls = 0
    showUser(2)
}

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

suspend fun loadName(): String { delay(100); return "Ada" }
suspend fun loadAge(): Int { delay(100); return 36 }

fun task4() = runBlocking {
    println("\n=== Task 4: async ===")

    val sequential = measureTimeMillis {
        val name = loadName()
        val age = loadAge()
        println("sequential: $name, $age")
    }
    println("Sequential time: $sequential ms")

    val parallel = measureTimeMillis {
        val name = async { loadName() }
        val age = async { loadAge() }
        println("async + late await: ${name.await()}, ${age.await()}")
    }
    println("Async time: $parallel ms")

    val awaitEarly = measureTimeMillis {
        val name = async { loadName() }.await()
        val age = async { loadAge() }.await()
        println("async + early await: $name, $age")
    }
    println("Early await time: $awaitEarly ms")
}

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
    single.close()

    launch {
        println("No dispatcher (inherited): ${Thread.currentThread().name}")
    }.join()
}
