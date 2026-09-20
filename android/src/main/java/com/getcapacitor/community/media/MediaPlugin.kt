package com.getcapacitor.community.media

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Logger
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import okhttp3.OkHttpClient
import okhttp3.Request

@CapacitorPlugin(
    name = "Media",
    permissions = [
        Permission(
            strings = [Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE],
            alias = "publicStorage"
        ),
        Permission(
            strings = [Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO],
            alias = "publicStorage13Plus"
        )
    ]
)
public class MediaPlugin : Plugin() {
    // @todo
    @PluginMethod
    public fun getMedias(call: PluginCall) {
        call.unimplemented()
    }

    @PluginMethod
    public fun getMediaByIdentifier(call: PluginCall) {
        call.unimplemented("No need to do this on Android -- the identifier is the file path.")
    }

    @PluginMethod
    public fun getAlbums(call: PluginCall) {
        Log.d("DEBUG LOG", "GET ALBUMS")
        if (isStoragePermissionGranted) {
            Log.d("DEBUG LOG", "HAS PERMISSION")
            getAlbumsWithPermission(call)
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED")
            bridge.saveCall(call)
            requestAllPermissions(call, "permissionCallback")
        }
    }

    @PluginMethod
    public fun savePhoto(call: PluginCall) {
        Log.d("DEBUG LOG", "SAVE PHOTO TO ALBUM")
        if (isStoragePermissionGranted) {
            Log.d("DEBUG LOG", "HAS PERMISSION")
            saveMedia(call)
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED")
            bridge.saveCall(call)
            requestAllPermissions(call, "permissionCallback")
            Log.d("DEBUG LOG", "___SAVE PHOTO TO ALBUM AFTER PERMISSION REQUEST")
        }
    }

    @PluginMethod
    public fun saveVideo(call: PluginCall) {
        Log.d("DEBUG LOG", "SAVE VIDEO TO ALBUM")
        if (isStoragePermissionGranted) {
            Log.d("DEBUG LOG", "HAS PERMISSION")
            saveMedia(call)
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED")
            bridge.saveCall(call)
            requestAllPermissions(call, "permissionCallback")
        }
    }

    @PluginMethod
    public fun createAlbum(call: PluginCall) {
        Log.d("DEBUG LOG", "CREATE ALBUM")
        if (isStoragePermissionGranted) {
            Log.d("DEBUG LOG", "HAS PERMISSION")
            createAlbumWithPermission(call)
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED")
            bridge.saveCall(call)
            requestAllPermissions(call, "permissionCallback")
        }
    }

    @PermissionCallback
    private fun permissionCallback(call: PluginCall) {
        if (!isStoragePermissionGranted) {
            Logger.debug(logTag, "User denied storage permission")
            call.reject("Unable to complete operation; user denied permission request.", EC_ACCESS_DENIED)
            return
        }

        when (call.methodName) {
            "getMedias" -> call.unimplemented()
            "getAlbums" -> getAlbumsWithPermission(call)
            "savePhoto", "saveVideo" -> saveMedia(call)
            "createAlbum" -> createAlbumWithPermission(call)
        }
    }

    private val isGalleryMode: Boolean
        get() = config.getBoolean("androidGalleryMode", false)

    // If not in gallery mode, no permissions are required
    private val isStoragePermissionGranted: Boolean
        get() = !isGalleryMode || getPermissionState("publicStorage13Plus") == PermissionState.GRANTED

    private fun getAlbumsWithPermission(call: PluginCall) {
        Log.d("DEBUG LOG", "___GET ALBUMS")

        val response = JSObject()
        val albums = JSArray()
        val bucketIds = HashSet<String?>()
        val identifiers = HashSet<String?>()

        val projection =
            arrayOf(
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.DATA
            )

        val imageUri = if (isGalleryMode) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.INTERNAL_CONTENT_URI

        val videoUri = if (isGalleryMode) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.INTERNAL_CONTENT_URI

        val curs =
            arrayOf(
                activity.contentResolver.query(imageUri, projection, null, null, null),
                activity.contentResolver.query(videoUri, projection, null, null, null)
            )

        for (nullableCur in curs) {
            // The provider may answer with no cursor. That was a NullPointerException in the Java implementation too.
            val cur = nullableCur!!
            while (cur.moveToNext()) {
                val albumName = cur.getString(cur.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME))
                val bucketId = cur.getString(cur.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID))

                if (!bucketIds.contains(bucketId)) {
                    val path = cur.getString(cur.getColumnIndex(MediaStore.MediaColumns.DATA))
                    val fileForPath = File(path)
                    val album = JSObject()

                    album.put("name", albumName)
                    album.put("identifier", fileForPath.parent)
                    albums.put(album)

                    bucketIds.add(bucketId)
                    identifiers.add(fileForPath.parent)
                }
            }

            cur.close()
        }

        val albumPath = File(albumsPath)
        for (sub in albumPath.listFiles()) {
            if (sub.isDirectory && !identifiers.contains(sub.absolutePath)) {
                val album = JSObject()

                album.put("name", sub.name)
                album.put("identifier", sub.absolutePath)
                identifiers.add(sub.absolutePath)
                albums.put(album)
            }
        }

        response.put("albums", albums)
        Log.d("DEBUG LOG", response.toString())
        Log.d("DEBUG LOG", "___GET ALBUMS FINISHED")

        call.resolve(response)
    }

    @PluginMethod
    public fun getAlbumsPath(call: PluginCall) {
        val data = JSObject()
        data.put("path", albumsPath)
        call.resolve(data)
    }

    private val albumsPath: String
        get() = context.externalMediaDirs[0].absolutePath

    private fun saveMedia(call: PluginCall) {
        Log.d("DEBUG LOG", "___SAVE MEDIA TO ALBUM")
        val inputPath = call.getString("path")
        if (inputPath == null) {
            call.reject("Input file path is required", EC_ARG_ERROR)
            return
        }

        val inputFile =
            if (inputPath.startsWith("data:")) {
                createTempFileFromDataUrl(call, inputPath) ?: return
            } else if (inputPath.startsWith("http://") || inputPath.startsWith("https://")) {
                downloadToTempFile(call, inputPath) ?: return
            } else {
                // A URI without a path was a NullPointerException in the Java implementation too.
                File(Uri.parse(inputPath).path!!)
            }

        val album = call.getString("albumIdentifier")
        Log.d("SDK BUILD VERSION", Build.VERSION.SDK_INT.toString())

        if (album == null) {
            call.reject("Album identifier required", EC_ARG_ERROR)
            return
        }

        val albumDir = File(album)

        if (!albumDir.exists() || !albumDir.isDirectory) {
            call.reject("Album identifier does not exist, use getAlbums() to get", EC_ARG_ERROR)
            return
        }

        Log.d("ENV LOG - ALBUM DIR", albumDir.toString())

        try {
            // generate image file name using current date and time
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(Date())
            val fileName = call.getString("fileName", "IMG_$timeStamp")
            val expFile = copyFile(inputFile, albumDir, fileName)
            scanPhoto(expFile)

            val result = JSObject()
            result.put("filePath", expFile.toString())
            call.resolve(result)
        } catch (e: RuntimeException) {
            call.reject("Error occurred: $e", EC_ARG_ERROR)
        }
    }

    /** Returns null after rejecting [call]. */
    private fun createTempFileFromDataUrl(call: PluginCall, dataUrl: String): File? {
        try {
            val base64EncodedString = dataUrl.substring(dataUrl.indexOf(",") + 1)
            val decodedBytes = Base64.decode(base64EncodedString, Base64.DEFAULT)
            // Like String.split in Java, ignore trailing empty parts so that "data:" alone fails to parse.
            val mime = dataUrl.split(";", limit = 2)[0].split(":").dropLastWhile { it.isEmpty() }[1]
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (extension.isNullOrEmpty()) {
                call.reject("Cannot identify media type to save image.", EC_ARG_ERROR)
                return null
            }

            try {
                val inputFile = File.createTempFile("tmp", ".$extension", context.cacheDir)
                val os = FileOutputStream(inputFile)
                os.write(decodedBytes)
                os.close()
                return inputFile
            } catch (e: IOException) {
                call.reject("Temporary file creation from data URL failed", EC_FS_ERROR)
                return null
            }
        } catch (e: Exception) {
            call.reject("Data URL parsing failed.", EC_ARG_ERROR)
            return null
        }
    }

    /** Returns null after rejecting [call]. */
    private fun downloadToTempFile(call: PluginCall, url: String): File? {
        val client = OkHttpClient()
        val okrequest = Request.Builder().url(url).build()
        try {
            // Download image
            val response = client.newCall(okrequest).execute()
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw IOException()
            }

            // Get file extension from URL
            var extension: String? = MimeTypeMap.getFileExtensionFromUrl(url)
            // If it doesn't have it there,
            // attempt to pull extension from MIME type
            if (extension.isNullOrEmpty()) {
                val mt = body.contentType()
                if (mt == null) {
                    call.reject("Cannot identify media type to save image.", EC_ARG_ERROR)
                    return null
                }

                val mime = mt.type + "/" + mt.subtype
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            }

            // Still no extension? reject
            if (extension.isNullOrEmpty()) {
                call.reject("Cannot identify media type to save image.", EC_ARG_ERROR)
                return null
            }

            // Save to temp file
            try {
                val inputFile = File.createTempFile("tmp", ".$extension", context.cacheDir)

                body.byteStream().use { inputStream ->
                    FileOutputStream(inputFile).use { os ->
                        inputStream.copyTo(os)
                    }
                }
                return inputFile
            } catch (e: IOException) {
                call.reject("Saving download to device failed.", EC_FS_ERROR)
                return null
            }
        } catch (e: IOException) {
            call.reject("Download failed", EC_DOWNLOAD_ERROR)
            return null
        }
    }

    private fun createAlbumWithPermission(call: PluginCall) {
        Log.d("DEBUG LOG", "___CREATE ALBUM")
        val folderName = call.getString("name")

        if (folderName == null) {
            call.reject("Album name must be given!", EC_ARG_ERROR)
            return
        }

        val f = File(albumsPath, folderName)

        if (!f.exists()) {
            if (!f.mkdir()) {
                Log.d("DEBUG LOG", "___ERROR ALBUM")
                call.reject("Cant create album", EC_FS_ERROR)
            } else {
                Log.d("DEBUG LOG", "___SUCCESS ALBUM CREATED")
                call.resolve()
            }
        } else {
            Log.d("DEBUG LOG", "___ERROR ALBUM ALREADY EXISTS")
            call.reject("Album already exists", EC_FS_ERROR)
        }
    }

    private fun copyFile(inputFile: File, albumDir: File, fileName: String?): File {
        // if destination folder does not exist, create it
        if (!albumDir.exists()) {
            if (!albumDir.mkdir()) {
                throw RuntimeException("Destination folder does not exist and cannot be created.")
            }
        }

        val absolutePath = inputFile.absolutePath
        val extension = absolutePath.substring(absolutePath.lastIndexOf("."))

        val newFile = File(albumDir, fileName + extension)

        // Read and write image files
        val inChannel: FileChannel
        val outChannel: FileChannel

        try {
            inChannel = FileInputStream(inputFile).channel
        } catch (e: FileNotFoundException) {
            throw RuntimeException("Source file not found: " + inputFile + ", error: " + e.message)
        }
        try {
            outChannel = FileOutputStream(newFile).channel
        } catch (e: FileNotFoundException) {
            throw RuntimeException("Copy file not found: " + newFile + ", error: " + e.message)
        }

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel)
        } catch (e: IOException) {
            throw RuntimeException("Error transfering file, error: " + e.message)
        } finally {
            try {
                inChannel.close()
            } catch (e: IOException) {
                Log.d("SaveImage", "Error closing input file channel: " + e.message)
                // does not harm, do nothing
            }
            try {
                outChannel.close()
            } catch (e: IOException) {
                Log.d("SaveImage", "Error closing output file channel: " + e.message)
                // does not harm, do nothing
            }
        }

        return newFile
    }

    private fun scanPhoto(imageFile: File) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.setData(Uri.fromFile(imageFile))
        activity.sendBroadcast(mediaScanIntent)
    }

    public companion object {
        public const val EC_ACCESS_DENIED: String = "accessDenied"
        public const val EC_ARG_ERROR: String = "argumentError"
        public const val EC_DOWNLOAD_ERROR: String = "downloadError"
        public const val EC_FS_ERROR: String = "filesystemError"
    }
}
