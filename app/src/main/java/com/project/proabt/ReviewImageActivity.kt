package com.project.proabt

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import com.project.proabt.databinding.ActivityReviewImageBinding
import com.project.proabt.models.Inbox
import com.project.proabt.models.Message
import com.project.proabt.models.User
import com.squareup.picasso.Picasso
import java.io.IOException


class ReviewImageActivity : AppCompatActivity() {
    private val friendId by lazy {
        intent.getStringExtra(UID)
    }
    private val name by lazy {
        intent.getStringExtra(NAME)
    }
    private val image by lazy {
        intent.getStringExtra(IMAGE)
    }
    private val sentphoto by lazy {
        Uri.parse(intent.getStringExtra("SENTPHOTO"))
    }
    val picturePath by lazy{
        intent.getStringExtra("PicturePath")
    }

    val storage by lazy {
        FirebaseStorage.getInstance()
    }
    val auth by lazy {
        FirebaseAuth.getInstance()
    }
    private val db by lazy {
        FirebaseDatabase.getInstance()
    }
    private val getUser by lazy {
        FirebaseFirestore.getInstance().document("users/$mCurrentUid")
    }
    private val mCurrentUid by lazy {
        FirebaseAuth.getInstance().uid!!
    }
    private lateinit var progressDialog: ProgressDialog
    var angle:Float?=0F
    lateinit var msgMap: Message
    lateinit var binding: ActivityReviewImageBinding
    lateinit var downloadUrl: String
    lateinit var currentUser: User
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra(UID, friendId)
            intent.putExtra(NAME, name)
            intent.putExtra(IMAGE, image)
            startActivity(intent)
        }
        FirebaseFirestore.getInstance().collection("users").document(mCurrentUid).get()
            .addOnSuccessListener {
                currentUser = it.toObject(User::class.java)!!
            }
        binding.btnSend.setOnClickListener {
            Log.d("ClickedFl", "Clicked")
            progressDialog = createProgressDialog("Sending a photo. Please wait", false)
            progressDialog.show()
            uploadImage(sentphoto)
        }
        angle=getOrientation(sentphoto).toFloat()
        Log.d("Rotation",angle.toString())
        binding.nameTv.text=name
        Picasso.get()
            .load(image)
            .placeholder(R.drawable.defaultavatar)
            .error(R.drawable.defaultavatar)
            .into(binding.userImgView)
        Picasso.get()
            .load(sentphoto)
            .rotate(angle!!)
            .placeholder(R.drawable.defaultavatar)
            .error(R.drawable.defaultavatar)
            .into(binding.sentImage)
    }

    private fun uploadImage(it: Uri) {
        val ref = storage.reference.child("uploads/images/" + System.currentTimeMillis())
        val uploadTask = ref.putFile(it)
        uploadTask.continueWithTask(Continuation<UploadTask.TaskSnapshot, Task<Uri>> { task ->
            if (!task.isSuccessful) {
                task.exception?.let {
                    throw it
                }
            }
            return@Continuation ref.downloadUrl
        }).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                downloadUrl = task.result.toString()
                sendMessage(downloadUrl)

            }
        }.addOnFailureListener {

        }
    }
    private fun sendMessage(msgUrl: String) {
        val id = getMessages(friendId!!).push().key
        checkNotNull(id) { "Cannot by null" }
        getUser.get().addOnSuccessListener {
            val imageUrl = it.get("imageUrl") as String
            val senderName = it.get("name") as String
            msgMap = Message(msgUrl, mCurrentUid, id, imageUrl, senderName, "IMAGE",angle = angle!!)
            Log.d("msgMap", msgMap.imageUrl)
            getMessages(friendId!!).child(id).setValue(msgMap).addOnSuccessListener {
                Log.d("CHATS", "Completed")
            }.addOnFailureListener {
                Log.d("CHATS", it.localizedMessage)
            }
            updateLastMessage(msgMap)
        }
    }

    private fun updateLastMessage(message: Message) {
        val inboxMap = Inbox(
            message.msg,
            friendId!!,
            name!!,
            image!!,
            count = 0,
            type = "IMAGE"
        )
        getInbox(mCurrentUid, friendId!!).setValue(inboxMap).addOnSuccessListener {
            getInbox(friendId!!, mCurrentUid).addListenerForSingleValueEvent(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val value = snapshot.getValue(Inbox::class.java)
                    inboxMap.apply {
                        from = message.senderId
                        name = currentUser.name
                        image = currentUser.thumbImage
                        count = 1
                    }
                    value?.let {
                        if (it.from == message.senderId) {
                            inboxMap.count = value.count + 1
                        }
                    }
                    getInbox(friendId!!, mCurrentUid).setValue(inboxMap)
                    val intent = Intent(this@ReviewImageActivity, ChatActivity::class.java)
                    intent.putExtra(UID, friendId)
                    intent.putExtra(NAME, name)
                    intent.putExtra(IMAGE, image)
                    if (::progressDialog.isInitialized)
                        progressDialog.dismiss()
                    startActivity(intent)
                }

                override fun onCancelled(error: DatabaseError) {
                }
            })
        }
    }

    private fun getId(friendId: String): String {
        return if (friendId > mCurrentUid) {
            mCurrentUid + friendId
        } else {
            friendId + mCurrentUid

        }
    }

    fun Context.createProgressDialog(message: String, isCancelable: Boolean): ProgressDialog {
        return ProgressDialog(this).apply {
            setCancelable(isCancelable)
            setCanceledOnTouchOutside(false)
            setMessage(message)
        }
    }

    private fun getInbox(toUser: String, fromUser: String) =
        db.reference.child("chats/$toUser/$fromUser")

    private fun getMessages(friendId: String) = db.reference.child("messages/${getId(friendId)}")
    private val ROTATION_DEGREES = 90
    @Throws(IOException::class)
    fun getOrientation(photoUri: Uri): Int {
        val exif = ExifInterface(picturePath!!)
        var orientation: Int = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> orientation = ROTATION_DEGREES
            ExifInterface.ORIENTATION_ROTATE_180 -> orientation = ROTATION_DEGREES * 2
            ExifInterface.ORIENTATION_ROTATE_270 -> orientation = ROTATION_DEGREES * 3
            else ->
                orientation = 0
        }
        return orientation
    }
}

