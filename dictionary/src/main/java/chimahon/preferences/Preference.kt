package chimahon.preferences

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

interface Preference<T> {
    fun key(): String
    fun get(): T
    fun set(value: T)
    fun isSet(): Boolean
    fun delete()
    fun defaultValue(): T
    fun changes(): Flow<T>
    fun stateIn(scope: CoroutineScope): StateFlow<T>

    companion object {
        private const val APP_STATE_PREFIX = "__APP_STATE_"
        private const val PRIVATE_PREFIX = "__PRIVATE_"

        fun appStateKey(key: String): String = "$APP_STATE_PREFIX$key"
        fun privateKey(key: String): String = "$PRIVATE_PREFIX$key"
    }
}

interface PreferenceStore {
    fun getString(key: String, defaultValue: String = ""): Preference<String>
    fun getLong(key: String, defaultValue: Long = 0L): Preference<Long>
    fun getInt(key: String, defaultValue: Int = 0): Preference<Int>
    fun getFloat(key: String, defaultValue: Float = 0f): Preference<Float>
    fun getBoolean(key: String, defaultValue: Boolean = false): Preference<Boolean>
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Preference<Set<String>>

    fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T>

    fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T>

    fun getAll(): Map<String, *>
}

class SharedPreferencesPreferenceStore(
    private val prefs: SharedPreferences,
) : PreferenceStore {

    override fun getString(key: String, defaultValue: String): Preference<String> =
        StringPref(prefs, key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        LongPref(prefs, key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        IntPref(prefs, key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        FloatPref(prefs, key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        BooleanPref(prefs, key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        StringSetPref(prefs, key, defaultValue)

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = ObjectStringPref(prefs, key, defaultValue, serializer, deserializer)

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> = ObjectIntPref(prefs, key, defaultValue, serializer, deserializer)

    override fun getAll(): Map<String, *> = prefs.all
}

private abstract class BasePref<T>(
    protected val prefs: SharedPreferences,
    private val prefKey: String,
    protected val default: T,
) : Preference<T> {

    override fun key(): String = prefKey

    override fun isSet(): Boolean = prefs.contains(prefKey)

    override fun delete() {
        prefs.edit().remove(prefKey).apply()
    }

    override fun defaultValue(): T = default

    override fun changes(): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == prefKey) trySend(get())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override fun stateIn(scope: CoroutineScope): StateFlow<T> =
        changes().stateIn(scope, SharingStarted.WhileSubscribed(5_000), get())
}

private class StringPref(
    prefs: SharedPreferences,
    key: String,
    default: String,
) : BasePref<String>(prefs, key, default) {
    override fun get(): String = prefs.getString(key(), default) ?: default
    override fun set(value: String) {
        prefs.edit().putString(key(), value).apply()
    }
}

private class LongPref(
    prefs: SharedPreferences,
    key: String,
    default: Long,
) : BasePref<Long>(prefs, key, default) {
    override fun get(): Long = prefs.getLong(key(), default)
    override fun set(value: Long) {
        prefs.edit().putLong(key(), value).apply()
    }
}

private class IntPref(
    prefs: SharedPreferences,
    key: String,
    default: Int,
) : BasePref<Int>(prefs, key, default) {
    override fun get(): Int = prefs.getInt(key(), default)
    override fun set(value: Int) {
        prefs.edit().putInt(key(), value).apply()
    }
}

private class FloatPref(
    prefs: SharedPreferences,
    key: String,
    default: Float,
) : BasePref<Float>(prefs, key, default) {
    override fun get(): Float = prefs.getFloat(key(), default)
    override fun set(value: Float) {
        prefs.edit().putFloat(key(), value).apply()
    }
}

private class BooleanPref(
    prefs: SharedPreferences,
    key: String,
    default: Boolean,
) : BasePref<Boolean>(prefs, key, default) {
    override fun get(): Boolean = prefs.getBoolean(key(), default)
    override fun set(value: Boolean) {
        prefs.edit().putBoolean(key(), value).apply()
    }
}

private class StringSetPref(
    prefs: SharedPreferences,
    key: String,
    default: Set<String>,
) : BasePref<Set<String>>(prefs, key, default) {
    override fun get(): Set<String> =
        prefs.getStringSet(key(), default)?.toSet() ?: default
    override fun set(value: Set<String>) {
        prefs.edit().putStringSet(key(), value).apply()
    }
}

private class ObjectStringPref<T>(
    prefs: SharedPreferences,
    key: String,
    default: T,
    private val serializer: (T) -> String,
    private val deserializer: (String) -> T,
) : BasePref<T>(prefs, key, default) {
    override fun get(): T {
        val raw = prefs.getString(key(), null) ?: return default
        return runCatching { deserializer(raw) }.getOrDefault(default)
    }
    override fun set(value: T) {
        prefs.edit().putString(key(), serializer(value)).apply()
    }
}

private class ObjectIntPref<T>(
    prefs: SharedPreferences,
    key: String,
    default: T,
    private val serializer: (T) -> Int,
    private val deserializer: (Int) -> T,
) : BasePref<T>(prefs, key, default) {
    override fun get(): T {
        if (!prefs.contains(key())) return default
        val raw = prefs.getInt(key(), 0)
        return runCatching { deserializer(raw) }.getOrDefault(default)
    }
    override fun set(value: T) {
        prefs.edit().putInt(key(), serializer(value)).apply()
    }
}
