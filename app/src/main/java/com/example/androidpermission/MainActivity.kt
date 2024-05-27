package com.example.androidpermission

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var requestPermissionButton:Button
    private lateinit var imageRecyclerView:RecyclerView
    private lateinit var addPhotos:Button
    private lateinit var imageAdapter: ImageAdapter
    private var selectedImages:List<Media>?=null
    private var imageList : MutableList<List<Media>> = mutableListOf()
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        requestPermissionButton = findViewById(R.id.getPermission)
        addPhotos = findViewById(R.id.AddPhotos)
        imageRecyclerView = findViewById(R.id.imageAdapter)
        imageRecyclerView.layoutManager = LinearLayoutManager(this)

        requestPermissionButton.setOnClickListener {
            requestForPermission()
        }
    }

    private fun requestForPermission() {
        // Permission request logic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.i(TAG, "requesting permission for android 14")
            requestPermissions.launch(arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.i(TAG, "requesting permission for tiramisu")
            requestPermissions.launch(arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO))
        } else {
            Log.i(TAG, "requesting permission for below 13")
            requestPermissions.launch(arrayOf(READ_EXTERNAL_STORAGE))
        }
    }

    // Register ActivityResult handler
    val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        Log.i(TAG, results.entries.toString())
        checkForPermission()
    }

    private fun checkForPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (
                    ContextCompat.checkSelfPermission(applicationContext, READ_MEDIA_IMAGES) == PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(applicationContext, READ_MEDIA_VIDEO) == PERMISSION_GRANTED
                    )
        ) {
            Log.i(TAG, "Full access for tiramisu 13")
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(applicationContext, READ_MEDIA_VISUAL_USER_SELECTED) == PERMISSION_GRANTED
        ) {
            Log.i(TAG, "partial access for android 14")
            loadImages()
        }  else if (ContextCompat.checkSelfPermission(applicationContext, READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            Log.i(TAG, "permission access for below 13")
        } else {
            Log.i(TAG, "access denied")
        }
    }

    private fun loadImages() {
        lifecycleScope.launch(Dispatchers.IO) {
            selectedImages = getImages(contentResolver)
            withContext(Dispatchers.Main) {
                if (selectedImages!!.isNotEmpty()) {
                    setRecyclerView(selectedImages!!)
                } else {
                    Log.i(TAG, "value is null or empty")
                }
            }
        }
    }

    private fun setRecyclerView(value: List<Media>) {
        Log.i(TAG, "calling setRecyclerView")
        imageAdapter = ImageAdapter(applicationContext, value)
        imageRecyclerView.adapter = imageAdapter
        Log.i(TAG, imageAdapter.itemCount.toString())
    }

    suspend fun getImages(contentResolver: ContentResolver): List<Media> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
        )

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Query all the device storage volumes instead of the primary only
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val images = mutableListOf<Media>()

        contentResolver.query(
            collectionUri,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collectionUri, cursor.getLong(idColumn))
                val name = cursor.getString(displayNameColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeTypeColumn)

                val image = Media(uri, name, size, mimeType)
                images.add(image)
            }
        }

        return@withContext images
    }

    override fun onResume() {
        super.onResume()
        imageRecyclerView.adapter?.notifyDataSetChanged()
    }
}