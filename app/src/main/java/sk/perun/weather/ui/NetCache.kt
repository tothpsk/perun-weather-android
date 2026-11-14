package sk.perun.weather.ui

import java.io.File
import java.security.MessageDigest

/**
 * Disk cache of Open-Meteo responses keyed by URL. Foreground loads read a fresh entry without touching the
 * network, fall back to a stale entry when the network fails, and the background worker refills it.
 */
internal object NetCache {
    @Volatile private var dir:File?=null
    /** Age up to which a cached response is served without a network request. */
    @Volatile var freshSeconds:Long=5*60
    /** Age up to which a cached response is used when the network request fails. */
    @Volatile var staleSeconds:Long=6*3600
    /** Set by the background worker so that its requests always reach the network. */
    @Volatile var forceNetwork:Boolean=false
    /** Pull-to-refresh moment: fresh entries written before it are not served, so a manual refresh always reaches the network. */
    @Volatile var invalidatedAt:Long=0L
    fun invalidate() { invalidatedAt=System.currentTimeMillis() }

    fun install(cacheDir:File) { dir=File(cacheDir,"net").apply { mkdirs() };runCatching { val limit=System.currentTimeMillis()-2*86400_000L;dir?.listFiles()?.forEach { if(it.lastModified()<limit) it.delete() } } }
    /** Alert feeds are cached only for the fresh period; a stale alert list is never shown when the network fails. */
    fun alertUrl(url:String):Boolean = url.contains("feeds.meteoalarm.org/") || url.contains("alerts.kde.org/") || url.contains("api.librewxr.net/v2/alerts")
    fun cacheable(url:String):Boolean = url.contains("open-meteo.com/") || alertUrl(url)
    private fun file(url:String):File? {
        val d=dir ?: return null
        val digest=MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return File(d,digest.joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') })
    }
    fun read(url:String,maxAgeSeconds:Long,honourInvalidation:Boolean=false):ByteArray? {
        val f=file(url) ?: return null
        if(!f.isFile) return null
        if(honourInvalidation && f.lastModified()<invalidatedAt) return null
        val age=(System.currentTimeMillis()-f.lastModified())/1000
        return if(age in 0..maxAgeSeconds) runCatching { f.readBytes() }.getOrNull() else null
    }
    fun write(url:String,data:ByteArray) {
        val f=file(url) ?: return
        runCatching { val tmp=File(f.path+".tmp");tmp.writeBytes(data);if(!tmp.renameTo(f)) { f.delete();tmp.renameTo(f) } }
    }
    fun clear() { dir?.listFiles()?.forEach { it.delete() } }
}
