package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

enum class PeerDataSourceType(val wireValue: String, val label: String) {
    OFFICIAL("official", "Official"),
    APP_COHORT("app_cohort", "App cohort"),
    HYBRID("hybrid", "Hybrid");

    companion object {
        fun fromWireValue(value: String?): PeerDataSourceType {
            return entries.firstOrNull { it.wireValue == value } ?: OFFICIAL
        }
    }
}

enum class PeerDataEntitlementSource {
    USER_FLAG,
    OPEN_BETA,
    LOCKED_PREVIEW
}

data class PeerDataEntitlementState(
    val isEnabled: Boolean = false,
    val source: PeerDataEntitlementSource = PeerDataEntitlementSource.LOCKED_PREVIEW,
    val message: String = "Peer Data is not enabled for this account yet."
)

data class PeerDataParticipationSettings(
    @DocumentId val id: String = "settings",
    val contributionEnabled: Boolean = false,
    val contributionVersion: String = "",
    val previewedAt: Long? = null,
    val withdrawnAt: Long? = null,
    val updatedAt: Long = 0L
)

data class PeerDataCitation(
    val id: String = "",
    val label: String = "",
    val url: String = "",
    val effectiveDate: String? = null,
    val lastReviewedDate: String? = null,
    val summary: String = ""
)

data class PeerBenchmarkDefinition(
    val id: String = "",
    val title: String = "",
    val summary: String = ""
)

data class PeerMethodologyNote(
    val id: String = "",
    val title: String = "",
    val body: String = ""
)

data class PeerDataChangelogEntry(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val effectiveDate: String = "",
    val citationId: String = ""
)

data class PeerDataBundle(
    val version: String = "",
    val generatedAt: Long = 0L,
    val lastReviewedAt: Long = 0L,
    val staleAfterDays: Int = 30,
    val sources: List<PeerDataCitation> = emptyList(),
    val benchmarkDefinitions: List<PeerBenchmarkDefinition> = emptyList(),
    val methodologyNotes: List<PeerMethodologyNote> = emptyList(),
    val freshnessSummary: String = "",
    val changelog: List<PeerDataChangelogEntry> = emptyList()
) {
    fun isStale(now: Long): Boolean {
        if (lastReviewedAt <= 0L) return true
        return now - lastReviewedAt > staleAfterDays * ONE_DAY_MILLIS
    }

    fun citationById(id: String): PeerDataCitation? = sources.firstOrNull { it.id == id }
}

data class PeerCohortDescriptor(
    val id: String = "",
    val label: String = "",
    val value: String = ""
)

data class PeerBenchmarkCard(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val source: String = PeerDataSourceType.OFFICIAL.wireValue,
    val cohortBasis: String = "",
    val sampleSizeBand: String? = null,
    val lastUpdatedAt: Long = 0L,
    val whatThisDoesNotMean: String = "",
    val citationIds: List<String> = emptyList()
) {
    val parsedSource: PeerDataSourceType
        get() = PeerDataSourceType.fromWireValue(source)
}

data class PeerOfficialContextCard(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val source: String = PeerDataSourceType.OFFICIAL.wireValue,
    val cohortBasis: String = "",
    val sampleSizeBand: String? = null,
    val lastUpdatedAt: Long = 0L,
    val whatThisDoesNotMean: String = "",
    val citationIds: List<String> = emptyList()
) {
    val parsedSource: PeerDataSourceType
        get() = PeerDataSourceType.fromWireValue(source)
}

data class PeerDataSnapshot(
    val snapshotId: String = "",
    val generatedAt: Long = 0L,
    val cohortDescriptors: List<PeerCohortDescriptor> = emptyList(),
    val benchmarkCards: List<PeerBenchmarkCard> = emptyList(),
    val officialContextCards: List<PeerOfficialContextCard> = emptyList(),
    val caveats: List<String> = emptyList(),
    val notEnoughSimilarPeers: Boolean = false
) {
    val primaryBenchmarkCard: PeerBenchmarkCard?
        get() = benchmarkCards.firstOrNull()

    val primaryOfficialCard: PeerOfficialContextCard?
        get() = officialContextCards.firstOrNull()
}

private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
