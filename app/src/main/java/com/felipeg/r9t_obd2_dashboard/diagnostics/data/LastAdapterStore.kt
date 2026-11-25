package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import android.content.Context
import androidx.core.content.edit

/** The adapter the rider chose last, so the next connection takes one tap. */
internal interface LastAdapterStore {
    fun load(): PairedAdapter?
    fun save(adapter: PairedAdapter)
}

internal class SharedPreferencesLastAdapterStore(context: Context) : LastAdapterStore {
    private val preferences = context.getSharedPreferences("elm-diagnostics", Context.MODE_PRIVATE)

    override fun load(): PairedAdapter? =
        preferences.getString(ID, null)?.let { PairedAdapter(it, preferences.getString(NAME, null)) }

    override fun save(adapter: PairedAdapter) {
        preferences.edit {
            putString(ID, adapter.id)
            putString(NAME, adapter.name)
        }
    }

    private companion object {
        const val ID = "last-adapter-id"
        const val NAME = "last-adapter-name"
    }
}
