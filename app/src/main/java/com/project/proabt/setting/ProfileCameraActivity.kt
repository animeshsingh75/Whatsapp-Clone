package com.project.proabt.setting

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.project.proabt.R
import com.project.proabt.databinding.ActivityProfileCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ProfileCameraActivity : AppCompatActivity() {
    lateinit var binding: ActivityProfileCameraBinding
    val mCurrentUid by lazy {
        FirebaseAuth.getInstance().uid!!
    }
    companion object {
        @JvmStatic
        val CAMERA_PERM_CODE = 422
    }
    private var imageCapture: ImageCapture? = null
    private fun askCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            CAMERA_PERM_CODE
        )
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            Runnable {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder().build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                } catch (ex: Exception) {
                    Log.e("CAM", "Error binding camera", ex)
                }

            },
            ContextCompat.getMainExecutor(this)

        )

    }

    private fun takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            return
        }
        val photoFile = File(getOutputDirectory(this),
            SimpleDateFormat(
                "yyyyMMddHHmmssSSS", Locale.US
            ).format(System.currentTimeMillis()))
        val outputFileOptions =
            ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture!!.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Log.d("PhotoUri", "PhotoUri:$savedUri")
                    Log.d("PhotoUri", "PhotoUri:${photoFile.absolutePath}")
                    val intent = Intent(this@ProfileCameraActivity, ReviewProfileActivity::class.java)
                    intent.putExtra("SENTPHOTO", savedUri.toString())
                    intent.putExtra("PicturePath", photoFile.absolutePath)
                    startActivity(intent)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@ProfileCameraActivity,
                        "Image Saving Failed",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    Log.e("CAM", "Image Saving Failed", exception)
                }

            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityProfileCameraBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        askCameraPermission()

        binding.btnTakePhoto.setOnClickListener { takePhoto() }

    }
    fun getOutputDirectory(context: Context): File {
        val appContext = context.applicationContext
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERM_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Permission Error")
                    .setMessage("Camera Permission not provided")
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
