package app.application.ports

/**
 * Port for analytics event tracking.
 * This is an output port — abstracts analytics so the application
 * does not depend on Google Analytics or any specific provider.
 */
interface AnalyticsPort {
    fun trackEvent(category: String, action: String, label: String = "")
}
