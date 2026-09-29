package app.lawnchair.preferences

/**
 * A lightweight [PrefEntry] backed by caller-provided storage.
 *
 * This keeps custom preference sources compatible with Lawnchair's native adapter/listener
 * lifecycle without changing the storage implementation.
 */
class CallbackPrefEntry<T>(
    override val key: String,
    override val defaultValue: T,
    private val getter: () -> T,
    private val setter: (T) -> Unit,
) : PrefEntry<T> {
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<PreferenceChangeListener>()

    override fun get(): T = getter()

    override fun set(newValue: T) {
        setter(newValue)
        listeners.forEach(PreferenceChangeListener::onPreferenceChange)
    }

    override fun addListener(listener: PreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PreferenceChangeListener) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        listeners.forEach(PreferenceChangeListener::onPreferenceChange)
    }
}
