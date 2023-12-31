package com.gulderbone.styletransfer

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.DequantizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class TfLiteStyleTransfer(
    private val context: Context,
) {

    private var styleBitmap: Bitmap? = null
    private var stylePredictInterpreter: Interpreter? = null
    private var styleTransferInterpreter: Interpreter? = null

    private val predictInputTargetHeight = 256
    private val predictInputTargetWidth = 256
    private val predictOutputShape = intArrayOf(1, 1, 1, 100)

    private val transferInputTargetHeight = 384
    private val transferInputTargetWidth = 384
    private val transferOutputShape = intArrayOf(1, 384, 384, 3)

    init {
        setupModels()
    }

    fun setStyleImage(bitmap: Bitmap) {
        styleBitmap = bitmap
    }

    fun transfer(bitmap: Bitmap): Bitmap? {
        val inputTensorImage = processInputImage(
            bitmap,
            transferInputTargetHeight,
            transferInputTargetWidth
        )
        val styleTensorImage = processInputImage(
            styleBitmap!!,
            predictInputTargetHeight,
            predictInputTargetWidth
        )

        // 1. Create buffer for the first model output
        val predictOutput = TensorBuffer.createFixedSize(
            predictOutputShape, DataType.FLOAT32
        )

        // 2. Run first model
        stylePredictInterpreter?.run(styleTensorImage?.buffer, predictOutput.buffer)

        val transformInput =
            arrayOf(inputTensorImage?.buffer, predictOutput.buffer)

        // 3. Create buffer for the second model output
        val outputImage = TensorBuffer.createFixedSize(
            transferOutputShape, DataType.FLOAT32
        )
        styleTransferInterpreter?.runForMultipleInputsOutputs( // 4. Get the final output
            transformInput,
            mapOf(0 to outputImage.buffer) // we don't care about other outputs
        )

        return getOutputImage(outputImage)
    }

    private fun processInputImage(
        image: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
    ): TensorImage? {
        val tensorImage = TensorImage(DataType.FLOAT32) // 1. Create TensorImage
        tensorImage.load(image) // 2. Load the image into TensorImage

        val height = image.height
        val width = image.width
        val cropSize = Integer.min(height, width)

        val imageProcessor = ImageProcessor.Builder() // 3. Create ImageProcessor
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(
                ResizeOp(
                    targetHeight,
                    targetWidth,
                    ResizeOp.ResizeMethod.BILINEAR
                )
            )
            .add(NormalizeOp(0f, 255f))
            .build()

        return imageProcessor.process(tensorImage) // 4. Process the image
    }

    private fun getOutputImage(output: TensorBuffer): Bitmap? {
        val imagePostProcessor = ImageProcessor.Builder()
            .add(DequantizeOp(0f, 255f)).build()
        val tensorImage = TensorImage(DataType.FLOAT32)

        tensorImage.load(output)

        return imagePostProcessor.process(tensorImage).bitmap
    }

    private fun setupModels() {
        val options = Interpreter.Options()
        options.numThreads = 2

        try {
            stylePredictInterpreter = Interpreter(
                FileUtil.loadMappedFile(
                    context,
                    "style_predict.tflite"
                ), options
            )
            styleTransferInterpreter = Interpreter(
                FileUtil.loadMappedFile(
                    context,
                    "style_transfer.tflite"
                ), options
            )
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }
}
