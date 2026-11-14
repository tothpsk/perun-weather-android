package sk.perun.weather.ui

import sk.perun.weather.R

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private const val MapPage = "https://appassets.androidplatform.net/assets/map/librewxr_map.html"
private const val ApiHost = "api.librewxr.net"
private const val MaxResponseBytes = 4_000_000
private const val TileHost = "tile.openstreetmap.org"
private const val TileMaxAgeMs = 7L * 24 * 3600 * 1000
private const val TileCacheMaxBytes = 60L * 1024 * 1024
private val TilePath = Regex("/[0-9]{1,2}/[0-9]{1,7}/[0-9]{1,7}[.]png")

private val MapFloatingControls = """
(function(){
  if(document.getElementById('perun-floating-controls'))return;
  const style=document.createElement('style');
  style.id='perun-floating-controls';
  style.textContent=`
    :root{--pw-footer:36px;--pw-dock:90px}
    #stage{min-height:0}
    #dock{position:absolute;z-index:1000;left:12px;right:12px;
      bottom:calc(var(--pw-footer) + 12px);box-sizing:border-box;
      border:1px solid #4B586C;border-radius:20px;
      padding:8px 12px 10px;background:rgba(32,40,51,.94);
      box-shadow:0 5px 18px rgba(0,0,0,.38);
      display:grid;grid-template-columns:auto minmax(0,1fr);
      column-gap:8px;row-gap:0;align-items:center}
    #dock .row{display:contents}
    #dock #play{grid-column:1;grid-row:1/span 2}
    #dock #slider{grid-column:2;grid-row:1;width:100%;box-sizing:border-box}
    #dock #lbl{grid-column:2;grid-row:2;font-size:13px;text-align:right;padding:0 4px 0 0;overflow-wrap:anywhere}
    #quick{bottom:calc(var(--pw-footer) + var(--pw-dock) + 24px)}
    #map .leaflet-bottom{bottom:4px}
    #map .leaflet-bottom.leaflet-left{left:8px}
    #map .leaflet-bottom.leaflet-right{right:8px;max-width:calc(100% - 128px)}
    #map .leaflet-control-scale{margin:0;max-width:104px}
    #map .leaflet-control-scale-line{max-width:104px;box-sizing:border-box}
    #map .leaflet-control-attribution{margin:0;float:none;
      max-width:100%;box-sizing:border-box;white-space:normal;
      overflow-wrap:anywhere;text-align:right;font-size:10px;line-height:1.3;
      border-radius:6px;padding:3px 5px}
    @media(max-height:360px){
      #dock{bottom:calc(var(--pw-footer) + 6px);padding:4px 10px}
      #quick{bottom:calc(var(--pw-footer) + var(--pw-dock) + 12px);gap:4px}
    }
  `;
  document.head.appendChild(style);
  const root=document.documentElement;
  const dock=document.getElementById('dock');
  const map=document.getElementById('map');
  let pending=false;
  function measure(){
    pending=false;
    const attr=map&&map.querySelector('.leaflet-control-attribution');
    const scale=map&&map.querySelector('.leaflet-control-scale');
    const footer=Math.ceil(Math.max(attr?attr.getBoundingClientRect().height:0,scale?scale.getBoundingClientRect().height:0)+8);
    const height=Math.ceil(dock?dock.getBoundingClientRect().height:0);
    const a=footer+'px',b=height+'px';
    if(root.style.getPropertyValue('--pw-footer')!==a)root.style.setProperty('--pw-footer',a);
    if(root.style.getPropertyValue('--pw-dock')!==b)root.style.setProperty('--pw-dock',b);
  }
  function schedule(){if(!pending){pending=true;requestAnimationFrame(measure)}}
  if(window.ResizeObserver){
    const resize=new ResizeObserver(schedule);
    if(dock)resize.observe(dock);
    if(map)resize.observe(map);
    const observed=new WeakSet();
    function watch(){
      if(map)map.querySelectorAll('.leaflet-control-attribution,.leaflet-control-scale').forEach(el=>{
        if(!observed.has(el)){observed.add(el);resize.observe(el)}
      });
      schedule();
    }
    if(map)new MutationObserver(watch).observe(map,{childList:true,subtree:true,characterData:true});
    watch();
  }
  window.addEventListener('resize',schedule);
  if(window.visualViewport)window.visualViewport.addEventListener('resize',schedule);
  schedule();
})();
""".trimIndent()

// A page opened from file:// sends no Referer, which OpenStreetMap's tile servers block.
// Native apps identify themselves with a User-Agent instead (fetchBytes sets one), and tiles are cached for 7 days.
private fun trimTileCache(dir: File) {
    val files = dir.listFiles() ?: return
    var total = files.sumOf { it.length() }
    if (total <= TileCacheMaxBytes) return
    for (f in files.sortedBy { it.lastModified() }) {
        if (total <= TileCacheMaxBytes * 3 / 4) break
        total -= f.length()
        f.delete()
    }
}

private fun osmTile(context: Context, uri: Uri): WebResourceResponse? {
    val path = uri.path ?: return null
    if (uri.scheme != "https" || uri.host != TileHost || !TilePath.matches(path)) return null
    val dir = File(context.cacheDir, "osm-tiles").apply { mkdirs() }
    val name = path.trim('/').replace('/', '_')
    val file = File(dir, name)
    val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < TileMaxAgeMs
    val bytes: ByteArray = if (fresh) {
        file.readBytes()
    } else {
        try {
            val data = fetchBytes(uri.toString())
            val tmp = File(dir, name + ".tmp" + Thread.currentThread().id)
            tmp.writeBytes(data)
            if (!tmp.renameTo(file)) tmp.delete()
            trimTileCache(dir)
            data
        } catch (e: Exception) {
            if (file.isFile) file.readBytes()
            else return WebResourceResponse("image/png", null, 503, "Tile unavailable", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
    }
    return WebResourceResponse("image/png", null, ByteArrayInputStream(bytes))
}

// The page may only ask for https://api.librewxr.net data; the native side does the request, so CORS is not needed.
private class MapBridge(private val webView: () -> WebView?, private val onReady: () -> Unit) {
    private val pool = Executors.newFixedThreadPool(2)

    @JavascriptInterface
    fun ready() {
        webView()?.post { onReady() }
    }

    @JavascriptInterface
    fun fetchText(id: Int, url: String) {
        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
        if (uri == null || uri.scheme != "https" || uri.host != ApiHost || uri.userInfo != null) {
            reply(id, false, uiText(R.string.ui_blokovane_a6121cb7))
            return
        }
        pool.execute {
            try {
                val bytes = fetchBytes(url)
                if (bytes.size > MaxResponseBytes) throw IllegalStateException(uiText(R.string.ui_prilis_velka_odpoved_8aad5e07))
                reply(id, true, String(bytes, Charsets.UTF_8))
            } catch (e: Exception) {
                reply(id, false, e.message ?: uiText(R.string.ui_chyba_b9f17311))
            }
        }
    }

    private fun reply(id: Int, ok: Boolean, text: String) {
        val view = webView() ?: return
        view.post {
            if (webView() === view) view.evaluateJavascript("window.__pwFetchDone($id,$ok,${JSONObject.quote(text)})", null)
        }
    }

    fun shutdown() {
        pool.shutdownNow()
    }
}

// The bridge is created inside the factory with a concrete type; its methods carry @JavascriptInterface,
// so the lint warning about an unresolved generic type is a false positive.
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface", "JavascriptInterface")
@Composable
internal fun MapsScreen(location: Location) {
    var ready by remember { mutableStateOf(false) }
    val views = remember { arrayOfNulls<WebView>(1) }
    val bridges = remember { arrayOfNulls<MapBridge>(1) }
    DisposableEffect(Unit) {
        onDispose {
            bridges[0]?.shutdown()
            bridges[0] = null
            views[0]?.let { view ->
                view.removeJavascriptInterface("PerunBridge")
                view.stopLoading()
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            views[0] = null
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val bridge = MapBridge({ views[0] }, { ready = true })
            bridges[0] = bridge
            WebView(ctx).apply {
                views[0] = this
                setBackgroundColor(0xFF202833.toInt())
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportZoom(false)
                    builtInZoomControls = false
                    userAgentString = "$userAgentString PerunWeatherAndroid/0.1 (+https://github.com/tothpsk/perun-weather-android)"
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (url == MapPage) view.evaluateJavascript(MapFloatingControls, null)
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        when {
                            request.method != "GET" -> null
                            request.url.toString() == MapPage -> WebResourceResponse(
                                "text/html", "UTF-8", ByteArrayInputStream(localizedMapPage(ctx))
                            )
                            else -> osmTile(ctx.applicationContext, request.url)
                        }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.toString() == MapPage) return false
                        if (uri.scheme == "https") {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        return true
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = true
                }
                addJavascriptInterface(bridge, "PerunBridge")
                loadUrl(MapPage)
            }
        },
    )
    LaunchedEffect(ready, location) {
        if (ready) {
            val js = String.format(Locale.US, "window.PerunMap.init({lat:%.5f,lon:%.5f,name:%s})", location.latitude, location.longitude, JSONObject.quote(location.name))
            views[0]?.evaluateJavascript(js, null)
        }
    }
}
