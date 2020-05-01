package com.example.cameraxsample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Size
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.android.synthetic.main.activity_main.*
import org.xml.sax.Parser
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit




private const val REQUEST_CODE_PERMISSIONS = 10
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)





class MainActivity : AppCompatActivity(), LifecycleOwner {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        viewFinder = findViewById(R.id.view_finder)

        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }


    }


        private val executor = Executors.newSingleThreadExecutor()
        private lateinit var viewFinder: TextureView


        private fun startCamera() {
            val previewConfig = PreviewConfig.Builder().apply {
                setTargetResolution(Size(640, 480))
            }.build()

            val preview = Preview(previewConfig)

            preview.setOnPreviewOutputUpdateListener {

                // To update the SurfaceTexture, we have to remove it and re-add it
                val parent = viewFinder.parent as ViewGroup
                parent.removeView(viewFinder)
                parent.addView(viewFinder, 0)

                viewFinder.surfaceTexture = it.surfaceTexture
                updateTransform()
            }


            val imageCaptureConfig = ImageCaptureConfig.Builder()
                .apply {
                    setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                }.build()

            val imageCapture = ImageCapture(imageCaptureConfig)

            findViewById<ImageButton>(R.id.capture_button).setOnClickListener {
                //val file = File(externalMediaDirs.first(),
                val fileDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CAMERAX_SAMPLE")
                fileDir.mkdirs()

                //val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                //    "CAMERAX_SAMPLE/${System.currentTimeMillis()}.jpg")

                val file = File(fileDir, "${System.currentTimeMillis()}.jpg")
                imageCapture.takePicture(file, executor, object :ImageCapture.OnImageSavedListener{


                    override fun onError(imageCaptureError: ImageCapture.ImageCaptureError, message: String, exc: Throwable?) {

                        val msg = "Photo capture failed: $message"
                        Log.e("CameraXApp", msg, exc)
                        viewFinder.post {
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onImageSaved(file: File) {
                        val msg = "Photo capture succeeded: ${file.absolutePath}"
                        Log.d("CameraXApp", msg)
                        viewFinder.post {
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()



                            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
                                val f = File(file.absolutePath)
                                mediaScanIntent.data = Uri.fromFile(f)
                                sendBroadcast(mediaScanIntent)
                            }


                            //撮った写真を描画
                            imageView.setImageURI(Uri.parse(file.absolutePath))

                        }

                    }
                })
            }

            CameraX.bindToLifecycle(this, preview, imageCapture)
        }

        private fun updateTransform() {

            val matrix = Matrix()

            // Compute the center of the view finder
            val centerX = viewFinder.width / 2f
            val centerY = viewFinder.height / 2f

            // Correct preview output to account for display rotation
            val rotationDegrees = when(viewFinder.display.rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> return
            }
            matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

            // Finally, apply transformations to our TextureView
            viewFinder.setTransform(matrix)

        }



        override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
            if (requestCode == REQUEST_CODE_PERMISSIONS) {
                if (allPermissionsGranted()) {
                    viewFinder.post { startCamera() }
                } else {
                    Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}
