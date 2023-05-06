package com.anuraagpotdaar.teacherconnect.facerecognitionhelper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.anuraagpotdaar.teacherconnect.model.FaceNetModel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

class FrameAnalyser(
    context: Context,
    private var boundingBoxOverlay: BoundingBoxOverlay,
    private var model: FaceNetModel,
    private val onAttendanceUpdateListener: OnAttendanceUpdateListener
) : ImageAnalysis.Analyzer {

    private val realTimeOpts =
        FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    private val detector = FaceDetection.getClient(realTimeOpts)

    private val nameScoreHashmap = HashMap<String, ArrayList<Float>>()
    private var subject = FloatArray(model.embeddingDim)

    private var isProcessing = false

    private var faceRecognized = false

    var faceList = ArrayList<Pair<String, FloatArray>>()

    private var t1: Long = 0L

    private val metricToBeUsed = "l2"

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        if (isProcessing || faceList.size == 0 || faceRecognized) {
            image.close()
            return
        } else {
            isProcessing = true

            var frameBitmap =
                BitmapUtils.imageToBitmap(image.image!!, image.imageInfo.rotationDegrees)

            frameBitmap =
                BitmapUtils.rotateBitmap(frameBitmap, image.imageInfo.rotationDegrees.toFloat())

            if (!boundingBoxOverlay.areDimsInit) {
                boundingBoxOverlay.frameHeight = frameBitmap.height
                boundingBoxOverlay.frameWidth = frameBitmap.width
            }

            val inputImage = InputImage.fromBitmap(frameBitmap, 0)
            detector.process(inputImage).addOnSuccessListener { faces ->
                    CoroutineScope(Dispatchers.Default).launch {
                        runModel(faces, frameBitmap)
                    }
                }.addOnCompleteListener {
                    image.close()
                }
        }
    }


    private suspend fun runModel(faces: List<Face>, cameraFrameBitmap: Bitmap) {
        withContext(Dispatchers.Default) {
            t1 = System.currentTimeMillis()
            val predictions = ArrayList<Prediction>()
            for (face in faces) {
                try {
                    val croppedBitmap =
                        BitmapUtils.cropRectFromBitmap(cameraFrameBitmap, face.boundingBox)
                    subject = model.getFaceEmbedding(croppedBitmap)
                    for (i in 0 until faceList.size) {
                        if (nameScoreHashmap[faceList[i].first] == null) {
                            val p = ArrayList<Float>()
                            if (metricToBeUsed == "cosine") {
                                p.add(cosineSimilarity(subject, faceList[i].second))
                            } else {
                                p.add(L2Norm(subject, faceList[i].second))
                            }
                            nameScoreHashmap[faceList[i].first] = p
                        } else {
                            if (metricToBeUsed == "cosine") {
                                nameScoreHashmap[faceList[i].first]?.add(
                                    cosineSimilarity(
                                        subject,
                                        faceList[i].second
                                    )
                                )
                            } else {
                                nameScoreHashmap[faceList[i].first]?.add(
                                    L2Norm(
                                        subject,
                                        faceList[i].second
                                    )
                                )
                            }
                        }
                    }

                    val avgScores =
                        nameScoreHashmap.values.map { scores -> scores.toFloatArray().average() }
                    Logger.log("Average score for each user : $nameScoreHashmap")

                    val names = nameScoreHashmap.keys.toTypedArray()
                    nameScoreHashmap.clear()

                    val bestScoreUserName: String = if (metricToBeUsed == "cosine") {
                        if (avgScores.maxOrNull()!! > model.model.cosineThreshold) {
                            names[avgScores.indexOf(avgScores.maxOrNull()!!)]
                        } else {
                            "Unknown"
                        }
                    } else {
                        if (avgScores.minOrNull()!! > model.model.l2Threshold) {
                            "Unknown"
                        } else {
                            names[avgScores.indexOf(avgScores.minOrNull()!!)]
                        }
                    }
                    Logger.log("Person identified as $bestScoreUserName")
                    if (bestScoreUserName != "Unknown") {
                        Logger.log("Person identified as $bestScoreUserName")

                        val currentTime = Calendar.getInstance().time
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
                        val todayDate = dateFormat.format(currentTime)
                        val currentTimeString = timeFormat.format(currentTime)

                        val firestore = FirebaseFirestore.getInstance()
                        val documentRef = firestore.collection("teachers").document(bestScoreUserName)
                        val attendanceData = mapOf(
                            todayDate to mapOf(
                                "time" to currentTimeString
                            )
                        )

                        documentRef.update("attendance", FieldValue.arrayUnion(attendanceData))
                            .addOnSuccessListener {
                                Logger.log("Attendance time saved successfully")
                                onAttendanceUpdateListener.onAttendanceUpdated(true)
                                faceRecognized = true
                            }.addOnFailureListener { exception ->
                                Logger.log("Error saving attendance time: $exception")
                            }

                        predictions.add(
                            Prediction(
                                face.boundingBox, bestScoreUserName
                            )
                        )

                        break
                    } else {
                        predictions.add(
                            Prediction(
                                face.boundingBox, bestScoreUserName
                            )
                        )
                    }

                } catch (e: Exception) {
                    Log.e("Model", "Exception in FrameAnalyser : ${e.message}")
                    continue
                }
                Log.e("Performance", "Inference time -> ${System.currentTimeMillis() - t1}")
            }
            withContext(Dispatchers.Main) {
                boundingBoxOverlay.faceBoundingBoxes = predictions
                boundingBoxOverlay.invalidate()
                isProcessing = false
            }
        }
    }

    private fun L2Norm(x1: FloatArray, x2: FloatArray): Float {
        return sqrt(x1.mapIndexed { i, xi -> (xi - x2[i]).pow(2) }.sum())
    }

    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
        val mag1 = sqrt(x1.map { it * it }.sum())
        val mag2 = sqrt(x2.map { it * it }.sum())
        val dot = x1.mapIndexed { i, xi -> xi * x2[i] }.sum()
        return dot / (mag1 * mag2)
    }

    interface OnAttendanceUpdateListener {
        fun onAttendanceUpdated(success: Boolean)
    }
}
