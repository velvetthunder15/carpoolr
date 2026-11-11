package com.carpoolr.app.profile

import android.content.Context
import android.net.Uri
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.nio.charset.StandardCharsets

/**
 * Scans an Aadhaar QR code either via Google Play Services' zero-permission camera UI
 * or from an e-Aadhaar / mAadhaar screenshot picked from the phone's gallery.
 */
object AadhaarScanner {

    fun scanWithCamera(
        context: Context,
        onQr: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                    ?: barcode.rawBytes?.let { String(it, StandardCharsets.ISO_8859_1) }
                if (!raw.isNullOrBlank()) onQr(raw) else onError("couldn't read qr contents")
            }
            .addOnFailureListener { e ->
                if (e is ApiException && e.statusCode == CommonStatusCodes.CANCELED) return@addOnFailureListener
                val msg = e.message.orEmpty()
                if (msg.contains("download", ignoreCase = true) || msg.contains("unavailable", ignoreCase = true)) {
                    onError("google qr scanner is downloading · try again in a few seconds, or pick a screenshot")
                } else {
                    onError("couldn't open qr scanner · you can also pick an e-aadhaar screenshot")
                }
            }
    }

    fun scanFromImageUri(
        context: Context,
        uri: Uri,
        onQr: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
            onError("couldn't read that image")
            return
        }
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val hit = barcodes.firstNotNullOfOrNull { b ->
                    b.rawValue ?: b.rawBytes?.let { String(it, StandardCharsets.ISO_8859_1) }
                }
                if (!hit.isNullOrBlank()) onQr(hit)
                else onError("no qr code found in that image · crop closer to the aadhaar qr")
            }
            .addOnFailureListener { e ->
                val msg = e.message.orEmpty()
                if (msg.contains("download", ignoreCase = true) || msg.contains("unavailable", ignoreCase = true)) {
                    onError("barcode model is finishing setup · try again in a moment")
                } else {
                    onError("couldn't scan that image")
                }
            }
            .addOnCompleteListener { scanner.close() }
    }
}
