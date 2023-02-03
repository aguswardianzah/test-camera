package com.example.testcamera

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.testcamera.databinding.ActivityMainBinding
import com.google.firebase.ktx.Firebase
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.storage.ktx.storage
import java.util.concurrent.Executors

@ExperimentalGetImage
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "TestCamera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private val viewBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val cameraExecutor by lazy { Executors.newSingleThreadExecutor() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(viewBinding.root)

        // Request permissions
        if (allPermissionsGranted())
            startCamera()
        else
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
    }

    private fun degreesToFirebaseRotation(degrees: Int): Int {
        return when (degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw IllegalArgumentException(
                "Rotation must be 0, 90, 180, or 270."
            )
        }
    }

    // Preview
    private val imagePreview by lazy {
        Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }
    }

    private val imageAnalysis by lazy {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {
                setAnalyzer(cameraExecutor) {
                    image = it;
                }
            }
    }

    private lateinit var image: ImageProxy
    private fun takePhoto() {
        // show loading
        loading()

        //changing normal degrees into Firebase rotation
        val rotationDegrees = degreesToFirebaseRotation(image.imageInfo.rotationDegrees)

        //Getting a FirebaseVisionImage object using the Image object and rotationDegrees
        val mediaImage = image.image ?: return
        val images = FirebaseVisionImage.fromMediaImage(mediaImage, rotationDegrees)

        //Getting bitmap from FirebaseVisionImage Object
        val bitmap = images.bitmap

        //initializing FirebaseVisionTextRecognizer object
        val detector = FirebaseVision.getInstance().onDeviceTextRecognizer

        //Passing FirebaseVisionImage Object created from the cropped bitmap
        detector.processImage(FirebaseVisionImage.fromBitmap(bitmap))
            .addOnSuccessListener { firebaseVisionText ->
                //getting decoded text
                val text = firebaseVisionText.text

//                Toast.makeText(this, "result: $text", Toast.LENGTH_SHORT).show()

                val storage = Firebase.storage
                // Create a storage reference from our app
                val fileRef = storage.reference.child("result.txt")

                fileRef.putBytes(text.toByteArray())
                    .continueWithTask {
                        if (!it.isSuccessful) {
                            it.exception?.let { e -> throw e }
                        }

                        fileRef.downloadUrl
                    }
                    .addOnCompleteListener {
                        dismissLoading()
                        startActivity(Intent(this@MainActivity, ResultActivity::class.java))
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Error", e.toString())
            }
            .addOnCompleteListener {
                image.close()
            }
    }

    private fun startCamera() {
        val provider = ProcessCameraProvider.getInstance(this)

        provider.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = provider.get()

            try {
                cameraProvider.apply {
                    // Unbind use cases before rebinding
                    unbindAll()

                    // Bind use cases to camera
                    bindToLifecycle(
                        this@MainActivity,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        imagePreview,
                        imageAnalysis
                    )
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS)
            if (allPermissionsGranted())
                startCamera()
            else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        else
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    var loadingDialog: ProgressDialog? = null
    private fun loading(
        title: String = "Mohon tunggu",
        msg: String = "Sedang memproses ...",
        indeterminate: Boolean = true
    ) {
        dismissLoading()
        loadingDialog = ProgressDialog.show(this, title, msg, indeterminate)
    }

    private fun dismissLoading() {
        loadingDialog?.dismiss()
    }
}