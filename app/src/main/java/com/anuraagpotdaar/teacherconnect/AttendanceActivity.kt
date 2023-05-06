package com.anuraagpotdaar.teacherconnect

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.anuraagpotdaar.teacherconnect.databinding.ActivityAttendanceBinding
import com.anuraagpotdaar.teacherconnect.facerecognitionhelper.FrameAnalyser
import com.anuraagpotdaar.teacherconnect.facerecognitionhelper.Logger
import com.anuraagpotdaar.teacherconnect.model.FaceNetModel
import com.anuraagpotdaar.teacherconnect.model.Models
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.*
import java.util.concurrent.Executors
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AttendanceActivity : AppCompatActivity(), FrameAnalyser.OnAttendanceUpdateListener {

    private var isSerializedDataStored = false
    private val SERIALIZED_DATA_FILENAME = "image_data"
    private lateinit var activityAttendanceBinding: ActivityAttendanceBinding
    private lateinit var previewView: PreviewView
    private lateinit var frameAnalyser: FrameAnalyser
    private lateinit var faceNetModel: FaceNetModel
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private val useGpu = true
    private val useXNNPack = true
    private val modelInfo = Models.FACENET
    private val cameraFacing = CameraSelector.LENS_FACING_FRONT
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseStorage = FirebaseStorage.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var matchedId: String
    companion object {
        lateinit var logTextView: TextView

        fun setMessage(message: String) {
            logTextView.text = message
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        matchedId = SharedPreferencesUtil.getSavedIdFromSharedPreferences(this).toString()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Request location permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),1000
            )
        } else {
            getLastKnownLocation()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController!!
                .hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        activityAttendanceBinding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(activityAttendanceBinding.root)

        previewView = activityAttendanceBinding.previewView
        logTextView = activityAttendanceBinding.logTextview
        val boundingBoxOverlay = activityAttendanceBinding.bboxOverlay
        boundingBoxOverlay.cameraFacing = cameraFacing
        boundingBoxOverlay.setWillNotDraw(false)
        boundingBoxOverlay.setZOrderOnTop(true)

        faceNetModel = FaceNetModel(this, modelInfo, useGpu, useXNNPack)
        frameAnalyser = FrameAnalyser(this, boundingBoxOverlay, faceNetModel, this)

        isSerializedDataStored = SharedPreferencesUtil.getDataStoredStatus(this)


        if (!isSerializedDataStored) {
            GlobalScope.launch(Dispatchers.IO) {
                signInAnonymously()

                val referenceImageUri = fetchReferenceImageUri()
                val referenceImage = downloadImageFromStorage(referenceImageUri)
                saveImageData(referenceImage, matchedId)

                withContext(Dispatchers.Main) {
                    isSerializedDataStored = true
                    SharedPreferencesUtil.saveDataStoredStatus(this@AttendanceActivity, true)
                    frameAnalyser.faceList = loadSerializedImageData()
                    // Call the setupCamera() function
                    setupCamera()
                }
            }
        } else {
            // Call the setupCamera() function
            setupCamera()
            frameAnalyser.faceList = loadSerializedImageData()
        }
        Logger.log("onCreate: Finished")

    }
    private fun getLastKnownLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    activityAttendanceBinding.tvGPScoordinates.text = "Coordinates: ${location.latitude}, ${location.longitude}"
                } else {
                    activityAttendanceBinding.tvGPScoordinates.text = "Coordinates: Not available"
                    showDialog("Coordinates: Not available\nTry enabling Location access")
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastKnownLocation()
            } else {
                activityAttendanceBinding.tvGPScoordinates.text = "Coordinates: Permission denied"
                showDialog("Coordinates: Permission denied")
            }
        }
    }

    private fun showDialog(msg: String) {
        if (!isFinishing && !isDestroyed) {
            MaterialAlertDialogBuilder(this)
                .setMessage(msg)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
                .show()
        }
    }

    private fun setupCamera() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 100
            )
            return
        }

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }


    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        Logger.log("AttendanceActivity: Binding camera preview")
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(Executors.newFixedThreadPool(4), frameAnalyser)

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
    }

    private suspend fun signInAnonymously() {
        firebaseAuth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (!task.isSuccessful) {
                    Logger.log("signInAnonymously:FAILURE")
                }
            }.await()
    }

    private suspend fun fetchReferenceImageUri(): Uri {

        val storageRef = firebaseStorage.reference
        val imageRef = storageRef.child("photoIDs/${matchedId.toString()}.jpg")
        return imageRef.downloadUrl.await()
    }

    private suspend fun downloadImageFromStorage(uri: Uri): Bitmap {
        return withContext(Dispatchers.IO) {
            val requestOptions = RequestOptions()
                .override(640, 480)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)

            Glide.with(this@AttendanceActivity)
                .asBitmap()
                .load(uri)
                .apply(requestOptions)
                .submit()
                .get()
        }
    }

    private fun saveImageData(referenceImage: Bitmap, username: String) {
        val faceEmbedding = faceNetModel.getFaceEmbedding(referenceImage)
        val data = arrayListOf(Pair(username, faceEmbedding))

        val serializedDataFile = File(filesDir, SERIALIZED_DATA_FILENAME)
        ObjectOutputStream(FileOutputStream(serializedDataFile)).apply {
            writeObject(data)
            flush()
            close()
        }
        SharedPreferencesUtil.saveDataStoredStatus(this, true)
        Logger.log("Saved image data")
    }

    private fun loadSerializedImageData(): ArrayList<Pair<String, FloatArray>> {
        val serializedDataFile = File(filesDir, SERIALIZED_DATA_FILENAME)
        val objectInputStream = ObjectInputStream(FileInputStream(serializedDataFile))
        val data = objectInputStream.readObject() as ArrayList<Pair<String, FloatArray>>
        objectInputStream.close()
        Logger.log("Loaded serialized image data")
        return data
    }

    override fun onAttendanceUpdated(success: Boolean) {
        if (success) {
            showDialog("Attendance marked successfully")
        } else {
            // Handle the failure case, if needed
        }
    }

}