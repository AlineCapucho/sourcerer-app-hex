package app

import app.infrastructure.cli.CliAdapter
import app.infrastructure.config.Container
import java.security.GeneralSecurityException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Application entry point.
 *
 * Composition Root: Creates the dependency container (which wires all
 * adapters to ports) and delegates execution to the CLI input adapter.
 *
 * Flow:
 *   main() → Container (wires adapters) → CliAdapter (parses input)
 *          → Use Cases (orchestrate) → Domain (business logic)
 *          → Ports → Adapters (external systems)
 */
fun main(argv: Array<String>) {
    Thread.setDefaultUncaughtExceptionHandler { _, e: Throwable ->
        System.err.println("Uncaught exception: ${e.message}")
    }

    if (BuildConfig.ENV != "production") {
        disableSslChecks()
    }

    // Create the composition root — all dependencies are wired here.
    val container = Container()

    // Delegate to the CLI input adapter.
    CliAdapter(container).run(argv)
}

/**
 * Disables SSL certificate validation for non-production environments.
 */
private fun disableSslChecks() {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(certs: Array<X509Certificate>,
                                        authType: String) {}
        override fun checkServerTrusted(certs: Array<X509Certificate>,
                                        authType: String) {}
    })

    try {
        val sc = SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
    } catch (e: GeneralSecurityException) {}

    HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
}
