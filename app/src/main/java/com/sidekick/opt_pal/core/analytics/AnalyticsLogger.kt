package com.sidekick.opt_pal.core.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase

object AnalyticsLogger {

    private inline fun withAnalytics(block: FirebaseAnalytics.() -> Unit) {
        runCatching { Firebase.analytics }
            .onSuccess { analytics -> runCatching { analytics.block() } }
    }

    fun logScreenView(screenName: String) {
        withAnalytics {
            logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                param(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
            }
        }
    }

    fun logEmploymentSaved(employerName: String) {
        withAnalytics {
            logEvent("employment_saved") {
                param("employer_name", employerName.take(80))
            }
        }
    }

    fun logReportingCompleted(obligationId: String) {
        withAnalytics {
            logEvent("reporting_completed") {
                param("obligation_id", obligationId)
            }
        }
    }

    fun logDocumentUploaded(tag: String) {
        withAnalytics {
            logEvent("document_uploaded") {
                param("document_tag", tag.take(80))
            }
        }
    }

    fun logDocumentDeleted(documentId: String) {
        withAnalytics {
            logEvent("document_deleted") {
                param("document_id", documentId)
            }
        }
    }

    fun logFeedbackSubmitted(rating: Int?) {
        withAnalytics {
            logEvent("feedback_submitted") {
                rating?.let { param("rating", it.toString()) }
            }
        }
    }

    fun logSetupCompleted(optType: String) {
        withAnalytics {
            logEvent("setup_completed") {
                param("opt_type", optType)
            }
        }
    }

    fun logManualReportingCreated(eventType: String) {
        withAnalytics {
            logEvent("reporting_manual_created") {
                param("event_type", eventType)
            }
        }
    }
}
