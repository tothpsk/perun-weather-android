from pathlib import Path
root=Path('app/src/main'); ui=root/'java/sk/perun/weather/ui'; edits={}
def rep(file,old,new):
 p=ui/file; s=edits.get(p,p.read_text()); assert s.count(old)==1,(file,old); edits[p]=s.replace(old,new)
d='OverviewData.kt'; o='Overview.kt'; f='ForecastScreen.kt'
rep(d,'val cloudMean: Double, val cloudMax:', 'val daylightCloud: Double = Double.NaN, val cloudMean: Double, val cloudMax:' )
# Append the optional field instead, preserving positional constructor calls.
rep(d,'val daylightCloud: Double = Double.NaN, val cloudMean: Double, val cloudMax: Double, val uv: Double, val aqi: Double,','val cloudMean: Double, val cloudMax: Double, val uv: Double, val aqi: Double, val daylightCloud: Double = Double.NaN,')
rep(d,'private val HOURLY_KEYS = listOf(', 'private val HOURLY_KEYS = listOf("is_day", ')
rep(d,'val cloud = h.series','val daylight = h.series("is_day", n)\n        val cloud = h.series')
rep(d,'cloudMean = vals(cloud).average(),','daylightCloud = cloudMedian(ix.filter { daylight[it]==1.0 }.map { cloud[it] }),\n                cloudMean = vals(cloud).average(),')
edits[ui/d]+='\ninternal fun cloudMedian(samples:List<Double>):Double {\n    val a=samples.filter { it in 0.0..100.0 }.sorted(); if(a.isEmpty()) return Double.NaN\n    return (a[(a.size-1)/2]+a[a.size/2])/2\n}\n'
rep(o,'today?.cloudMean','today?.daylightCloud')
rep(o,'cloudName(w.cloud).ifEmpty { uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61) },"cloud"','cloudName(w.cloud).ifEmpty { uiText(R.string.ui_udaje_nie_su_dostupne_4e729f61) }+"\\n"+uiText(R.string.cloud_median_note),"cloud"')
rep(f,'FV.Rain->day.sum(v.key);FV.Wind','FV.Cloud->cloudMedian(day.hours.filter { it.v("is_day")==1.0 }.map { it.v(v.key) });FV.Rain->day.sum(v.key);FV.Wind')
a='Text(mode.label,modifier=Modifier.fillMaxWidth().padding(top=6.dp,bottom=2.dp),color=FText,fontSize=20.sp,fontWeight=FontWeight.Bold)'
rep(f,a,'''if(mode!=FV.Cloud) '''+a+''' else Row(verticalAlignment=Alignment.CenterVertically) {
            Text(mode.label,color=FText,fontSize=20.sp,fontWeight=FontWeight.Bold)
            var openInfo by remember { mutableStateOf(false) }; val info=uiText(R.string.cloud_median_note)
            Box { IconButton(onClick={openInfo=true},modifier=Modifier.semantics { contentDescription=info }) { Text("ⓘ",color=ForecastMuted) }; DropdownMenu(expanded=openInfo,onDismissRequest={openInfo=false}) { Text(info,modifier=Modifier.widthIn(max=260.dp).padding(12.dp)) } }
        }''')
for folder,text in [('values','Denný údaj je medián hodinovej oblačnosti od východu do západu slnka.'),('values-en','The daily value is the median of hourly cloud cover between sunrise and sunset.')]:
 p=root/'res'/folder/'strings_cloud_median.xml'; assert not p.exists(); edits[p]='<resources><string name="cloud_median_note">'+text+'</string></resources>\n'
for p,s in edits.items(): p.write_text(s,encoding='utf-8')
