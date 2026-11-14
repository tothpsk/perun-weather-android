package sk.perun.weather.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Presentation only: never use translated text as an API or persisted key. */
internal object AppStrings {
    internal data class Lookup(
        val string: (Int, Array<out Any?>) -> String,
        val quantity: (Int, Int, Array<out Any?>) -> String,
    )
    @Volatile private var lookup: Lookup? = null

    fun install(resources: Resources) {
        lookup = Lookup(
            string = { id, args -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args) },
            quantity = { id, count, args ->
                if (args.isEmpty()) resources.getQuantityString(id, count)
                else resources.getQuantityString(id, count, *args)
            },
        )
    }
    internal fun installForTests(resolver: Lookup) { lookup = resolver }
    fun text(id: Int, args: Array<out Any?>): String = requireLookup().string(id, args)
    fun quantity(id: Int, count: Int, args: Array<out Any?>): String = requireLookup().quantity(id, count, args)
    private fun requireLookup(): Lookup = checkNotNull(lookup) {
        "AppStrings must be initialized by PerunApplication before presentation code runs"
    }
}

internal fun uiText(@StringRes id: Int, vararg args: Any?): String = AppStrings.text(id, args)
internal fun uiQuantityText(@PluralsRes id: Int, count: Int, vararg args: Any?): String = AppStrings.quantity(id, count, args)
