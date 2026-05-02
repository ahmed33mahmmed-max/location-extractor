package com.example.locationextractor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private lateinit var openMapButton: Button

    private var extractedLat: Double? = null
    private var extractedLng: Double? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageView.setImageURI(it)
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
            processImage(bitmap)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            imageView.setImageBitmap(it)
            processImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resultText = findViewById(R.id.resultText)
        openMapButton = findViewById(R.id.openMapButton)
        val galleryButton: Button = findViewById(R.id.galleryButton)
        val cameraButton: Button = findViewById(R.id.cameraButton)

        galleryButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA), 100)
            } else {
                cameraLauncher.launch(null)
            }
        }

        openMapButton.setOnClickListener {
            openInGoogleMaps()
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                extractCoordinates(rawText)
            }
            .addOnFailureListener {
                Toast.makeText(this, "OCR failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun extractCoordinates(text: String) {
        val normalized = normalizeDigits(text)

        val latRegex = Regex("""خط\s*العرض[\s:]*([\d.]+)""")
        val lngRegex = Regex("""خط\s*الطول[\s:]*([\d.]+)""")

        var lat = latRegex.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()
        var lng = lngRegex.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()

        if (lat == null || lng == null) {
            val pairRegex = Regex("""(-?\d{1,3}\.\d{3,})[\s,]+(-?\d{1,3}\.\d{3,})""")
            pairRegex.find(normalized)?.let {
                val a = it.groupValues[1].toDouble()
                val b = it.groupValues[2].toDouble()
                if (a in -90.0..90.0 && b in -180.0..180.0) {
                    lat = a; lng = b
                }
            }
        }

        if (lat != null && lng != null) {
            extractedLat = lat
            extractedLng = lng
            resultText.text = "Latitude: $lat\nLongitude: $lng"
            openMapButton.isEnabled = true
        } else {
            resultText.text = "No coordinates found.\n\nExtracted text:\n$text"
            openMapButton.isEnabled = false
        }
    }

    private fun normalizeDigits(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(when (ch) {
                in '٠'..'٩' -> ('0' + (ch - '٠'))
                in '۰'..'۹' -> ('0' + (ch - '۰'))
                else -> ch
            })
        }
        return sb.toString()
    }

    private fun openInGoogleMaps() {
        val lat = extractedLat ?: return
        val lng = extractedLng ?: return
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps?q=$lat,$lng")))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        }
    }
}
