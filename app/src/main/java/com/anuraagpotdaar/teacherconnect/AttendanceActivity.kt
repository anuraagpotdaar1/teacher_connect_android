package com.anuraagpotdaar.teacherconnect

import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.anuraagpotdaar.teacherconnect.databinding.ActivityAttendanceBinding
import com.anuraagpotdaar.teacherconnect.model.FaceNetModel
import com.anuraagpotdaar.teacherconnect.model.ModelInfo
import com.anuraagpotdaar.teacherconnect.model.Models
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AttendanceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAttendanceBinding
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.cameraPreview.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(cameraExecutor, ImageAnalyzer()) }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Use case binding failed", ex)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        private val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )

        private val username: String by lazy {
            intent.getStringExtra("Username") ?: throw IllegalStateException("Username not provided")
        }

        private val referenceBitmap: Bitmap by lazy {
            try {
                val inputStream = assets.open("${username.lowercase()}.jpg")
                BitmapFactory.decodeStream(inputStream)
            } catch (ex: IOException) {
                Log.e(TAG, "Failed to load reference image", ex)
                Toast.makeText(this@AttendanceActivity, "Reference image not found", Toast.LENGTH_SHORT).show()
                finish()
                throw IllegalStateException("Reference image not found")
            }
        }
        private val faceNetModel = FaceNetModel(
            this@AttendanceActivity,
            Models.FACENET,
            useGpu = false,
            useXNNPack = true
        )

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun  analyze(imageProxy: ImageProxy) {
            val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.size == 1) {
                        val face = faces.first()
                        val faceBitmap = cropFace(inputImage, imageProxy, face)
                        val faceEmbedding = faceNetModel.getFaceEmbedding(faceBitmap)
                        val referenceEmbedding = faceNetModel.getFaceEmbedding(referenceBitmap)

                        if (faceNetModel.isSamePerson(faceEmbedding, referenceEmbedding)) {
                            runOnUiThread {
                                Toast.makeText(this@AttendanceActivity, "Image matched", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            runOnUiThread {
                                binding.tvCoordinates.text = "Not matched"
                            }
                        }
                    } else {
                        runOnUiThread {
                            binding.tvCoordinates.text = "Not matched"
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "AttendanceActivity"
    }
}

private fun cropFace(inputImage: InputImage, imageProxy: ImageProxy, face: com.google.mlkit.vision.face.Face): Bitmap {
    val faceBoundingBox = face.boundingBox
    val bitmap = imageProxy.toBitmap()

    val left = faceBoundingBox.left.coerceAtLeast(0)
    val top = faceBoundingBox.top.coerceAtLeast(0)
    val right = (faceBoundingBox.left + faceBoundingBox.width()).coerceAtMost(bitmap.width)
    val bottom = (faceBoundingBox.top + faceBoundingBox.height()).coerceAtMost(bitmap.height)

    val width = right - left
    val height = bottom - top

    return Bitmap.createBitmap(bitmap, left, top, width, height)
}


