package org.demoth.cake.profile

/** Apply and persist a menu edit; restore the previous runtime and saved selection on failure.
 * The application checks session eligibility before calling this operation.
 */
internal object ProfileModChanges {
    fun apply(
        previous: CakeGameProfile,
        next: CakeGameProfile,
        previousAutomount: Boolean,
        automount: Boolean,
        applyContent: (CakeGameProfile, Boolean) -> Unit,
        saveSelection: (CakeGameProfile) -> Unit,
        saveSettings: () -> Unit,
    ) {
        require(previous.id == next.id) { "A mod edit cannot switch profiles" }
        var selectionWriteAttempted = false
        var settingsWriteAttempted = false
        try {
            applyContent(next, automount)
            if (previous != next) {
                selectionWriteAttempted = true
                saveSelection(next)
            }
            if (previousAutomount != automount) {
                settingsWriteAttempted = true
                saveSettings()
            }
        } catch (failure: Exception) {
            fun restore(action: () -> Unit) {
                try { action() } catch (rollback: Exception) { failure.addSuppressed(rollback) }
            }
            restore { applyContent(previous, previousAutomount) }
            if (selectionWriteAttempted) restore { saveSelection(previous) }
            if (settingsWriteAttempted) restore(saveSettings)
            throw failure
        }
    }
}
