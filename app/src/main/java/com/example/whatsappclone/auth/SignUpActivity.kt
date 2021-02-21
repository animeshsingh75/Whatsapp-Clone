package com.example.whatsappclone.auth

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.whatsappclone.MainActivity
import com.example.whatsappclone.models.User
import com.example.whatsappclone.databinding.ActivitySignUpBinding
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView


class SignUpActivity : AppCompatActivity() {
    lateinit var binding: ActivitySignUpBinding
    lateinit var downloadUrl: String
    lateinit var thumbnailUrl: String

    val storage by lazy {
        FirebaseStorage.getInstance()
    }
    val auth by lazy {
        FirebaseAuth.getInstance()
    }
    val database by lazy {
        FirebaseFirestore.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.userImgView.setOnClickListener {
            checkPermissionForImage()
        }
        binding.nextBtn.setOnClickListener {
            binding.nextBtn.isEnabled = false
            val name = binding.nameEt.text.toString()
            if (name.isEmpty()) {
                Toast.makeText(this, "Name cannot by empty", Toast.LENGTH_LONG).show()
            } else if (!::downloadUrl.isInitialized) {
                Toast.makeText(this, "Image cannot by empty", Toast.LENGTH_LONG).show()
            } else {
                val user = User(name, downloadUrl, thumbnailUrl, auth.uid!!)
                database.collection("users").document(auth.uid!!).set(user)
                    .addOnSuccessListener {
                        startActivity(Intent(this, MainActivity::class.java))
                    }.addOnFailureListener {
                        binding.nextBtn.isEnabled = true
                    }
            }
        }
    }

    override fun onBackPressed() {
    }

    private fun checkPermissionForImage() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED && (checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_DENIED)
        ) {
            val permission = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            val permissionWrite = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            requestPermissions(
                permission,
                1001
            )
            requestPermissions(
                permissionWrite,
                1002
            )
        } else {
            pickImageFromGallery()
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(
            intent,
            1000
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 1000) {
            val imageUri = data?.data
            startCrop(imageUri)
        }
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            val result = CropImage.getActivityResult(data)
            if (resultCode == RESULT_OK) {
                binding.userImgView.setImageURI(result.uri)
                uploadImage(result.uri)
            }
        }
    }

    private fun startCrop(imageUri: Uri?) {
        CropImage.activity(imageUri)
            .setGuidelines(CropImageView.Guidelines.ON)
            .setAspectRatio(1, 1)
            .start(this)
    }

    private fun uploadImage(it: Uri) {
        binding.nextBtn.isEnabled = false
        val ref = storage.reference.child("uploads/" + auth.uid.toString())
        val uploadTask = ref.putFile(it)
        uploadTask.continueWithTask(Continuation<UploadTask.TaskSnapshot, Task<Uri>> { task ->
            if (!task.isSuccessful) {
                task.exception?.let {
                    throw it
                }
            }
            return@Continuation ref.downloadUrl
        }).addOnCompleteListener { task ->
            binding.nextBtn.isEnabled = true
            if (task.isSuccessful) {
                downloadUrl = task.result.toString()
                Log.i("URL", "downloadUrl: $downloadUrl")
                getThumbnailUrl()
            }
        }.addOnFailureListener {

        }
    }

    private fun getThumbnailUrl() {
        val ref =
            storage.reference.child("uploads/" + auth.uid.toString() + "_120x120").downloadUrl.addOnCompleteListener {
                thumbnailUrl = it.result.toString()
                Log.i("URL", "thumbnailUrl: $thumbnailUrl")
            }

    }
}