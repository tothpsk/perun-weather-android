import re
from pathlib import Path
ui=Path('app/src/main/java/sk/perun/weather/ui')
test=Path('app/src/test/java/sk/perun/weather/ui')
files={}
def edit(base,name,pattern,replacement):
    p=base/(name+'.kt')
    s=files.get(p,p.read_text(encoding='utf-8'))
    s,n=re.subn(pattern,lambda _:replacement,s)
    if n!=1: raise SystemExit(f'{name}: expected one match, got {n}')
    files[p]=s
edit(ui,'Widgets',r'if\(kind==MeteogramWidget::class.java\) "forecast" else "overview"','"overview"')
edit(ui,'MarineOverview',r'cardinal16\(bearing\)','cardinal(bearing)')
edit(ui,'AlertsScreen',r'alertHazard\(a.event\+" "\+a.title,a.awareness\)','alertHazard(a.event,a.awareness,a.title)')
edit(ui,'Overview',r'alertHazard\(first.event\+" "\+g.label,first.awareness\)','alertHazard(first.event,first.awareness,g.label)')
edit(ui,'AlertsScreen',r'import androidx.compose.ui.text.AnnotatedString\n','import androidx.compose.ui.text.AnnotatedString\nimport androidx.compose.ui.text.LinkAnnotation\nimport androidx.compose.ui.text.TextLinkStyles\nimport androidx.compose.ui.text.withLink\n')
edit(ui,'AlertsScreen',r'(?m)^                if \(a.origin.isNotBlank\(\)\) Text[^\n]*\n                val page=a.originUri[^\n]*\n                if \(page!=null\) Text[^\n]*','\n'.join([
'                if (a.origin.isNotBlank()) {',
'                    val label=uiText(R.string.alert_origin,a.origin)',
'                    val page=a.originUri.takeIf { it.startsWith("https://") || it.startsWith("http://") }',
'                    val at=label.lastIndexOf(a.origin)',
'                    val text=buildAnnotatedString {',
'                        if (page==null || at<0) append(label) else {',
'                            append(label.substring(0,at))',
'                            withLink(LinkAnnotation.Url(page,TextLinkStyles(style=SpanStyle(color=SelectedEdge)))) { append(a.origin) }',
'                            append(label.substring(at+a.origin.length))',
'                        }',
'                    }',
'                    Text(text,fontSize=13.sp,color=ForecastMuted)',
'                }']))
edit(ui,'AlertsScreen',r'import androidx.compose.ui.platform.LocalUriHandler\n','')
edit(ui,'AlertsScreen',r'val uri=LocalUriHandler.current; ','')
edit(ui,'AlertHazards',r'internal fun alertHazard\(text:String,awareness:String=""\):AlertHazard \{','internal fun alertHazard(text:String,awareness:String="",headline:String=""):AlertHazard {')
# A shared code for snow and ice: the name of the event decides, then the headline; a text with both words stays snow, as before.
edit(ui,'AlertHazards',r'    if\(byCode==AlertHazard.Snow\) return [^\n]+','\n'.join([
'    fun snowOrIce(value:String):AlertHazard? {',
'        val normalized=plain(value)',
'        val tokens=normalized.split(Regex("[^\\\\p{L}\\\\p{N}]+")).filter { it.isNotEmpty() }',
'        fun hasKind(kind:AlertHazard)=HazardWords.any { (k,terms)->k==kind && terms.any { matches(normalized,tokens,it) } }',
'        val ice=hasKind(AlertHazard.Ice)',
'        val snow=hasKind(AlertHazard.Snow)',
'        return when { snow->AlertHazard.Snow; ice->AlertHazard.Ice; else->null }',
'    }',
'    if(byCode==AlertHazard.Snow) return snowOrIce(text) ?: snowOrIce(headline) ?: AlertHazard.Snow']))
edit(ui,'AlertHazards',r'return HazardWords.firstOrNull \{ \(_,terms\)->terms.any \{ matches\(t,words,it\) \} \}\?\.first \?: AlertHazard.Generic','\n'.join([
'return HazardWords.firstOrNull { (_,terms)->terms.any { matches(t,words,it) } }?.first',
'        ?: if(headline.isNotBlank()) alertHazard(headline) else AlertHazard.Generic']))
edit(test,'AlertHazardTest',r'(?m)^        assertEquals\(AlertHazard\.Snow,kind\("Yellow Snow-ice Warning","2; snow-ice"\)\)\r?\n','\n'.join([
'        assertEquals(AlertHazard.Snow,kind("Yellow Snow-ice Warning","2; snow-ice"))',
'        assertEquals(AlertHazard.Ice,alertHazard("Moderate icing warning","2; snow-ice","Yellow Snow-ice Warning issued for Slovakia - Bratislava"))',
'        assertEquals(AlertHazard.Snow,alertHazard("Moderate snow warning","2; snow-ice","Yellow Snow-ice Warning issued for Slovakia - Bratislava"))',
'']))
for p,s in files.items(): p.write_text(s,encoding='utf-8')
