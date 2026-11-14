package sk.perun.weather.ui

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import sk.perun.weather.R

/** JVM tests use real default XML values, without an Android Context or duplicate translations. */
open class ResourceStringsTestBase {
    init { AppStrings.installForTests(XmlDefaultStrings.lookup) }
}

private object XmlDefaultStrings {
    private data class Catalogue(val strings: Map<Int, String>, val plurals: Map<Int, Map<String, String>>)
    private val catalogue: Catalogue by lazy {
        val dir = sequenceOf(File("src/main/res/values"), File("app/src/main/res/values"))
            .firstOrNull { it.isDirectory } ?: error("Default Android resources directory not found")
        val strings = mutableMapOf<Int, String>()
        val plurals = mutableMapOf<Int, Map<String, String>>()
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        dir.listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
            val nodes = factory.newDocumentBuilder().parse(file).documentElement.childNodes
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                val name = element.getAttribute("name")
                when (element.tagName) {
                    "string" -> strings[R.string::class.java.getField(name).getInt(null)] = decode(element.textContent)
                    "plurals" -> {
                        val values = mutableMapOf<String, String>()
                        val items = element.getElementsByTagName("item")
                        for (j in 0 until items.length) {
                            val item = items.item(j) as Element
                            values[item.getAttribute("quantity")] = decode(item.textContent)
                        }
                        plurals[R.plurals::class.java.getField(name).getInt(null)] = values
                    }
                }
            }
        }
        Catalogue(strings, plurals)
    }
    val lookup = AppStrings.Lookup(
        string = { id, args -> format(catalogue.strings.getValue(id), args) },
        quantity = { id, count, args ->
            val forms = catalogue.plurals.getValue(id)
            val category = when (count) { 1 -> "one"; in 2..4 -> "few"; else -> "other" }
            format(forms[category] ?: forms.getValue("other"), args)
        },
    )
    private fun format(value: String, args: Array<out Any?>): String =
        if (args.isEmpty()) value else String.format(Locale.forLanguageTag("sk-SK"), value, *args)

    private fun decode(raw: String): String {
        val s = if (raw.startsWith('"') && raw.endsWith('"')) raw.substring(1, raw.length - 1) else raw
        return buildString {
            var i = 0
            while (i < s.length) {
                if (s[i] == '\\' && i + 1 < s.length) {
                    i++
                    append(when (s[i]) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> s[i] })
                } else append(s[i])
                i++
            }
        }
    }
}
