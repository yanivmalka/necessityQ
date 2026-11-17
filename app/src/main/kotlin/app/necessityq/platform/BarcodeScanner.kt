package app.necessityq.platform

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await

/**
 * Camera barcode scanning through Google Play services' code scanner: the scanning screen is
 * provided by the system, so the app needs no camera permission and no camera code of its own.
 * The scanner module is installed with the app (see the manifest) or downloaded on first use.
 */
object BarcodeScanner {
    private val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_ITF,
        )
        .enableAutoZoom()
        .build()

    /** The scanned value, or null when the user closed the scanner. Throws when scanning is unavailable. */
    suspend fun scan(activityContext: Context): String? {
        val scanner = GmsBarcodeScanning.getClient(activityContext, options)
        return try {
            scanner.startScan().await().rawValue
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive() // a real cancellation of the caller is passed on
            null // the user backed out of the scanner
        }
    }
}
