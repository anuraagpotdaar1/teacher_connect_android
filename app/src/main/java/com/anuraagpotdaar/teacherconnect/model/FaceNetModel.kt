package com.anuraagpotdaar.teacherconnect.model

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import java.nio.ByteBuffer
import java.util.logging.Logger
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Utility class for FaceNet model
class FaceNetModel(context : Context,
                   var model : ModelInfo,
                   useGpu : Boolean,
                   useXNNPack : Boolean) {

    // Input image size for FaceNet model.
    private val imgSize = model.inputDims

    // Output embedding size
    val embeddingDim = model.outputDims

    private var interpreter : Interpreter
    private val imageTensorProcessor = ImageProcessor.Builder()
        .add( ResizeOp( imgSize , imgSize , ResizeOp.ResizeMethod.BILINEAR ) )
        .add( StandardizeOp() )
        .build()

    init {
        val interpreterOptions = Interpreter.Options().apply {
            if ( useGpu ) {
                if ( CompatibilityList().isDelegateSupportedOnThisDevice ) {
                    addDelegate( GpuDelegate( CompatibilityList().bestOptionsForThisDevice ))
                }
            }
            else {
                numThreads = 4
            }
            useXNNPACK = useXNNPack
            useNNAPI = true
        }
        interpreter = Interpreter(FileUtil.loadMappedFile(context, model.assetsFilename ) , interpreterOptions )
    }

    fun getFaceEmbedding( image : Bitmap ) : FloatArray {
        return runFaceNet( convertBitmapToBuffer( image ))[0]
    }

    private fun runFaceNet(inputs: Any): Array<FloatArray> {
        val t1 = System.currentTimeMillis()
        val faceNetModelOutputs = Array( 1 ){ FloatArray( embeddingDim ) }
        interpreter.run( inputs, faceNetModelOutputs )
        Log.i( "Performance" , "${model.name} Inference Speed in ms : ${System.currentTimeMillis() - t1}")
        return faceNetModelOutputs
    }

    private fun convertBitmapToBuffer( image : Bitmap) : ByteBuffer {
        return imageTensorProcessor.process( TensorImage.fromBitmap( image ) ).buffer
    }

    fun isSamePerson(embedding1: FloatArray, embedding2: FloatArray, threshold: Float = 0.8f): Boolean {
        val distance = distance(embedding1, embedding2)
        return distance < threshold
    }

    private fun distance(embedding1: FloatArray, embedding2: FloatArray): Float {
        var sum = 0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return sum
    }

    class StandardizeOp : TensorOperator {

        override fun apply(p0: TensorBuffer?): TensorBuffer {
            val pixels = p0!!.floatArray
            val mean = pixels.average().toFloat()
            var std = sqrt( pixels.map{ pi -> ( pi - mean ).pow( 2 ) }.sum() / pixels.size.toFloat() )
            std = max( std , 1f / sqrt( pixels.size.toFloat() ))
            for ( i in pixels.indices ) {
                pixels[ i ] = ( pixels[ i ] - mean ) / std
            }
            val output = TensorBufferFloat.createFixedSize( p0.shape , DataType.FLOAT32 )
            output.loadArray( pixels )
            return output
        }
    }
}
