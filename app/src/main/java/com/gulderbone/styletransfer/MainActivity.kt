package com.gulderbone.styletransfer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gulderbone.styletransfer.ui.theme.StyleTransferTheme
import java.nio.ByteBuffer

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasRequiredPermissions()) {
            requestPermissions(CAMERAX_PERMISSIONS, 0)
        }

        val sharedViewModel = SharedViewModel()


        setContent {
            StyleTransferTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "MainScreen") {
                    composable("MainScreen") { MainScreen(sharedViewModel, navController) }
                    composable("StyleScreen") { StyleScreen(sharedViewModel) }
                }
            }
        }
    }

    @Composable
    fun MainScreen(sharedViewModel: SharedViewModel, navController: NavHostController) {
        val scaffoldState = rememberBottomSheetScaffoldState()
        val controller = remember {
            LifecycleCameraController(applicationContext).apply {
                setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 0.dp,
            sheetContent = {

            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                CameraPreview(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxSize()
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IconButton(onClick = {
                        takePhoto(controller) { bitmap ->
                            Log.d("CameraX", "Photo taken")
                            sharedViewModel.bitmap.value = bitmap
                            navController.navigate("StyleScreen")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Take a photo"
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun StyleScreen(sharedViewModel: SharedViewModel) {
        val tfLiteStyleTransfer = remember {
            TfLiteStyleTransfer(applicationContext)
        }

        val chosenImage = remember {
            mutableStateOf<Bitmap?>(null)
        }
        val imageChosen = remember {
            mutableStateOf(false)
        }

        val styles = mutableListOf<Bitmap>()

        assets.list("thumbnails")?.forEach {
            styles.add(
                BitmapFactory.decodeStream(assets.open("thumbnails/$it"))
            )
        }

        if (!imageChosen.value) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalItemSpacing = 16.dp,
            ) {
                items(styles) {
                    Box(
                        modifier = Modifier
                            .width(512.dp)
                            .height(512.dp)
                    ) {
                        Image(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    chosenImage.value = it
                                    imageChosen.value = true
                                },
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Style",
                        )
                    }
                }
            }
        } else {
            tfLiteStyleTransfer.setStyleImage(chosenImage.value!!)

            val bitmap = remember {
                sharedViewModel.bitmap.value
            }

            if (bitmap != null) {
                val transferredBitmap = tfLiteStyleTransfer.transfer(bitmap)
                if (transferredBitmap != null) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        bitmap = transferredBitmap.asImageBitmap(),
                        contentDescription = "Photo taken"
                    )
                }
            }
        }

    }

    private fun takePhoto(
        controller: LifecycleCameraController,
        onPhotoTaken: (Bitmap) -> Unit,
    ) {
        controller.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val processedBitmap = processImage(
                        image.planes[0].buffer,
                        image.imageInfo.rotationDegrees
                    )
                    onPhotoTaken(processedBitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.e("CameraX", "Error taking photo", exception)
                }
            }
        )
    }

    private fun processImage(buffer: ByteBuffer, rotation: Int): Bitmap {
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        var bitmapBuffer = BitmapFactory.decodeByteArray(
            bytes, 0,
            bytes.size, null
        )
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        bitmapBuffer = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer
                .width, bitmapBuffer.height, matrix, true
        )

        return bitmapBuffer
    }

    private fun hasRequiredPermissions(): Boolean = CAMERAX_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}
