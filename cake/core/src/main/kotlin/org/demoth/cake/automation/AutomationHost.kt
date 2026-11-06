package org.demoth.cake.automation

/** Small GL-thread boundary; tests may provide a standalone stage instead of a full client. */
interface AutomationHost {
    val automationUi: AutomationUi
    val automationCaptures: AutomationCaptures
    val presentedAutomationScreen: String?
    val isGamePresentationReady: Boolean
    fun openAutomationSample(game: Boolean?)
}
