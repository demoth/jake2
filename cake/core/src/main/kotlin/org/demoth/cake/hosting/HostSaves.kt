package org.demoth.cake.hosting

/** A reason is null when the corresponding singleplayer UI action is available. */
data class HostSaveAccess(val loadRejectionReason: String?, val saveRejectionReason: String?)

/** Metadata inspection is not a guarantee that all gameplay references can be restored. */
data class HostSaveEntry(
    val slot: String,
    val comment: String?,
    val mapName: String?,
    val savedAtMillis: Long?,
    val mode: HostMode?,
    val loadRejectionReason: String?,
    val mapTitle: String? = null,
) {
    val autosave: Boolean get() = slot == "save0"
}

sealed interface HostSaveCatalogResult {
    data class Available(val profileId: String, val entries: List<HostSaveEntry>) : HostSaveCatalogResult
    data class Rejected(val reason: String) : HostSaveCatalogResult
    data class Failed(val reason: String, val cause: Exception) : HostSaveCatalogResult
}

/** Completed means backend IO/restore completed; the client handshake may still be pending. */
sealed interface HostSaveResult {
    data class Completed(val slot: String) : HostSaveResult
    data class Rejected(val reason: String) : HostSaveResult
    data class Failed(val reason: String, val cause: Exception) : HostSaveResult
}
