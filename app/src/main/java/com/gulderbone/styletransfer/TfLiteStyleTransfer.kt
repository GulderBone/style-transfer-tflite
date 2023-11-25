package com.gulderbone.styletransfer

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
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
        val predictTensorImage = processInputImage(
            bitmap,
            predictInputTargetHeight,
            predictInputTargetWidth
        )

        val transferTensorImage = processInputImage(
            styleBitmap!!,
            transferInputTargetHeight,
            transferInputTargetWidth
        )

        val predictOutput = TensorBuffer.createFixedSize(
            predictOutputShape, DataType.FLOAT32
        )
        stylePredictInterpreter?.run(predictTensorImage?.buffer, predictOutput.buffer)

        val transformInput =
            arrayOf(transferTensorImage?.buffer, predictOutput.buffer)
        val outputImage = TensorBuffer.createFixedSize(
            transferOutputShape, DataType.FLOAT32
        )
        styleTransferInterpreter?.runForMultipleInputsOutputs(
            transformInput,
            mapOf(Pair(0, outputImage.buffer))
        )

        return getOutputImage(outputImage)
    }

    private fun processInputImage(
        image: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
    ): TensorImage? {
        val height = image.height
        val width = image.width
        val cropSize = Integer.min(height, width)

        val imageProcessor = ImageProcessor.Builder()
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

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(image)

        return imageProcessor.process(tensorImage)
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
