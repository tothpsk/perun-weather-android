package sk.perun.weather.ui

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Store of whole CAP messages by URL. A message at one address does not change (a change comes as a new message with
 * its own identifier), so it is kept until its last expires plus one hour and deleted after that.
 * The file name is the hash of the URL and the second until which the file is kept; URLs hold no coordinates.
 */
internal class CapStore(private val dir: File) {
    init { dir.mkdirs() }
    private fun hash(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun until(f: File): Long? = f.name.substringAfterLast('-', "").toLongOrNull()
    private fun files(url: String): List<File> { val h = hash(url) + "-"; return dir.listFiles()?.filter { it.name.startsWith(h) && !it.name.endsWith(".tmp") }.orEmpty() }

    fun read(url: String, now: Instant = Instant.now()): ByteArray? {
        for (f in files(url)) {
            val keep = until(f)
            if (keep == null || keep <= now.epochSecond) { f.delete(); continue }
            return runCatching { f.readBytes() }.getOrNull()
        }
        return null
    }

    fun write(url: String, data: ByteArray, expires: Instant, now: Instant = Instant.now()) {
        val keep = expires.epochSecond + 3600
        if (keep <= now.epochSecond) return
        files(url).forEach { it.delete() }
        val f = File(dir, hash(url) + "-" + keep)
        runCatching { val tmp = File(f.path + ".tmp"); tmp.writeBytes(data); if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) } }
        purge(now)
    }

    fun purge(now: Instant = Instant.now()) {
        dir.listFiles()?.forEach { f -> val keep = until(f); if (keep == null || keep <= now.epochSecond) f.delete() }
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}

internal object CapCache {
    @Volatile private var store: CapStore? = null
    fun install(cacheDir: File) { store = CapStore(File(cacheDir, "cap")).also { runCatching { it.purge() } } }
    fun read(url: String): ByteArray? = store?.read(url)
    fun write(url: String, data: ByteArray, expires: Instant) { store?.write(url, data, expires) }
    fun clear() { store?.clear() }
}

/** Downloads of new CAP messages allowed for one place in one refresh; messages from the store are free. */
internal class CapBudget(limit: Int = DefaultLimit) {
    private val left = AtomicInteger(limit)
    @Volatile var exhausted = false
        private set
    fun take(): Boolean = (left.getAndDecrement() > 0).also { if (!it) exhausted = true }
    companion object { const val DefaultLimit = 48 }
}

internal sealed interface CapFetch {
    class Read(val message: CapMessage) : CapFetch
    object OverBudget : CapFetch
    object Failed : CapFetch
}

/** A CAP message from the store, otherwise from the network within the budget; a readable one is stored until it expires. */
internal fun fetchCap(url: String, budget: CapBudget, download: (String) -> ByteArray = ::fetchBytes, cache: CapStore? = null): CapFetch {
    val stored = if (cache != null) cache.read(url) else CapCache.read(url)
    stored?.let { bytes -> parseCap(bytes)?.let { return CapFetch.Read(it) } }
    if (!budget.take()) return CapFetch.OverBudget
    val bytes = try { download(url) } catch (e: Exception) { return CapFetch.Failed }
    val message = parseCap(bytes) ?: return CapFetch.Failed
    message.infos.mapNotNull { it.expires }.maxOrNull()?.let { if (cache != null) cache.write(url, bytes, it) else CapCache.write(url, bytes, it) }
    return CapFetch.Read(message)
}

/** Runs [work] for every item, four at a time, and keeps the order of the items. */
internal fun <T, R> inParallel(items: List<T>, work: (T) -> R): List<R> {
    if (items.size <= 1) return items.map(work)
    val pool = Executors.newFixedThreadPool(4)
    try { return items.map { pool.submit(Callable { work(it) }) }.map { it.get() } } finally { pool.shutdown() }
}

/**
 * The areas of a message that belong to a place, recognised in every language: [pick] finds them in any info block,
 * and their codes then find the same areas in the other blocks, whose names are translated.
 */
internal fun CapMessage.areasLike(pick: (CapArea) -> Boolean): (CapArea) -> Boolean {
    val keys = infos.flatMap { it.areas }.filter(pick).flatMap { capAreaKeys(it) }.toSet()
    return { a -> val k = capAreaKeys(a); if (k.isEmpty()) pick(a) else k.any { it in keys } }
}
