package com.anuraagpotdaar.teacherconnect.facerecognitionhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.camera.core.CameraSelector
import androidx.core.graphics.toRectF

class BoundingBoxOverlay(context: Context, attributeSet: AttributeSet) :
    SurfaceView(context, attributeSet), SurfaceHolder.Callback {

    var areDimsInit = false
    var frameHeight = 0
    var frameWidth = 0

    var cameraFacing: Int = CameraSelector.LENS_FACING_FRONT

    var faceBoundingBoxes: ArrayList<Prediction>? = null

    private var output2OverlayTransform: Matrix = Matrix()

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#4D90caf9")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        strokeWidth = 2.0f
        textSize = 32f
        color = Color.WHITE
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        TODO("Not yet implemented")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        TODO("Not yet implemented")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        TODO("Not yet implemented")
    }

    override fun onDraw(canvas: Canvas?) {
        if (faceBoundingBoxes != null) {
            if (!areDimsInit) {
                val viewWidth = width.toFloat()
                val viewHeight = height.toFloat()
                val xFactor: Float = viewWidth / frameWidth.toFloat()
                val yFactor: Float = viewHeight / frameHeight.toFloat()
                output2OverlayTransform.preScale(xFactor, yFactor)
                if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
                    output2OverlayTransform.postScale(-1f, 1f, viewWidth / 2f, viewHeight / 2f)
                }
                areDimsInit = true
            } else {
                for (face in faceBoundingBoxes!!) {
                    val boundingBox = face.bbox.toRectF()
                    output2OverlayTransform.mapRect(boundingBox)
                    canvas?.drawRoundRect(boundingBox, 16f, 16f, boxPaint)
                    canvas?.drawText(
                        face.label,
                        boundingBox.centerX(),
                        boundingBox.centerY(),
                        textPaint,
                    )
                }
            }
        }
    }
}
