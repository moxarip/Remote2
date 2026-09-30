package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.StorageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Remote Backup", appName)
  }

  @Test
  fun `test storage formatting`() {
    assertEquals("0 B", StorageUtils.formatFileSize(0L))
    assertEquals("1 KB", StorageUtils.formatFileSize(1024L))
    assertEquals("1 MB", StorageUtils.formatFileSize(1024L * 1024L))
    assertTrue(StorageUtils.formatFileSize(34_600_000_000L).contains("GB"))
  }

  @Test
  fun `test file category classification`() {
    assertEquals("Photos", StorageUtils.getCategoryForFile("vacation.jpg", "image/jpeg"))
    assertEquals("Videos", StorageUtils.getCategoryForFile("movie.mp4", "video/mp4"))
    assertEquals("Documents", StorageUtils.getCategoryForFile("report.pdf", "application/pdf"))
    assertEquals("Other", StorageUtils.getCategoryForFile("archive.bin", "application/octet-stream"))
  }

  @Test
  fun `test device role values`() {
    assertEquals("HOST", com.example.models.DeviceRole.HOST.name)
    assertEquals("ADMIN", com.example.models.DeviceRole.ADMIN.name)
  }

  @Test
  fun `test admin downloads folder and file saving`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val folder = StorageUtils.getAdminDownloadsFolder(context)
    assertTrue(folder.exists())

    val vaultFile = com.example.models.VaultFile(
        fileId = "test_f1",
        name = "test_document.txt",
        relativePath = "Documents/test_document.txt",
        size = 1024L,
        mimeType = "text/plain",
        category = "Documents",
        isBackedUp = true
    )

    val savedFile = StorageUtils.downloadFileToAdmin(context, vaultFile)
    assertTrue(savedFile.exists())
    assertEquals("test_document.txt", savedFile.name)

    val downloadedList = StorageUtils.getAdminDownloadedFiles(context)
    assertTrue(downloadedList.any { it.name == "test_document.txt" })
  }

  @Test
  fun `test hierarchical folder partitioning and last modified sorting`() {
    val files = listOf(
        com.example.models.VaultFile(
            fileId = "f1",
            name = "root_file.txt",
            relativePath = "root_file.txt",
            lastModified = 1000L
        ),
        com.example.models.VaultFile(
            fileId = "f2",
            name = "newer_root.txt",
            relativePath = "newer_root.txt",
            lastModified = 5000L
        ),
        com.example.models.VaultFile(
            fileId = "f3",
            name = "photo1.jpg",
            relativePath = "Photos/photo1.jpg",
            lastModified = 2000L
        ),
        com.example.models.VaultFile(
            fileId = "f4",
            name = "photo2.jpg",
            relativePath = "Photos/Vacation/photo2.jpg",
            lastModified = 8000L
        )
    )

    // At root path:
    val (rootFolders, rootDirectFiles) = StorageUtils.getItemsForPath(files, "")
    // Subfolders should only be "Photos"
    assertEquals(1, rootFolders.size)
    assertEquals("Photos", rootFolders[0].name)
    assertEquals("Photos", rootFolders[0].relativePath)

    // Direct files at root should be root_file.txt and newer_root.txt, sorted by lastModified descending!
    assertEquals(2, rootDirectFiles.size)
    assertEquals("newer_root.txt", rootDirectFiles[0].name)
    assertEquals("root_file.txt", rootDirectFiles[1].name)

    // Inside "Photos" subfolder:
    val (photoSubfolders, photoDirectFiles) = StorageUtils.getItemsForPath(files, "Photos")
    assertEquals(1, photoSubfolders.size)
    assertEquals("Vacation", photoSubfolders[0].name)
    assertEquals(1, photoDirectFiles.size)
    assertEquals("photo1.jpg", photoDirectFiles[0].name)
  }

  @Test
  fun `test openInputStreamForVaultFile reads real file bytes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempFile = java.io.File(context.cacheDir, "sample_test_upload.txt")
    val expectedContent = "ACTUAL_FILE_CONTENT_BYTES_12345_XYZ"
    tempFile.writeText(expectedContent)

    val vaultFile = com.example.models.VaultFile(
        fileId = "test_physical_f1",
        name = "sample_test_upload.txt",
        relativePath = "sample_test_upload.txt",
        size = tempFile.length(),
        mimeType = "text/plain",
        uriString = android.net.Uri.fromFile(tempFile).toString()
    )

    val (stream, size) = StorageUtils.openInputStreamForVaultFile(context, vaultFile, emptyList())
    org.junit.Assert.assertNotNull(stream)
    val readContent = stream!!.bufferedReader().readText()
    assertEquals(expectedContent, readContent)
    assertEquals(tempFile.length(), size)
    stream.close()
  }

  @Test
  fun `test upload and download file bytes to cloud`() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val originalContent = "ACTUAL_UPLOAD_CONTENT_TEST_BYTES_FOR_FIREBASE"
      val testInputStream = java.io.ByteArrayInputStream(originalContent.toByteArray(Charsets.UTF_8))
      val testHostId = "test_host_unit_test_${System.currentTimeMillis()}"

      val vaultFile = com.example.models.VaultFile(
          fileId = "unit_test_file_${System.currentTimeMillis()}",
          name = "uploaded_document.txt",
          relativePath = "uploaded_document.txt",
          size = originalContent.length.toLong(),
          mimeType = "text/plain",
          category = "Documents",
          hostDeviceId = testHostId
      )

      // 1. Upload actual bytes to cloud storage
      val uploadResult = com.example.firebase.FirebaseManager.uploadFileToCloud(
          hostId = testHostId,
          file = vaultFile,
          inputStream = testInputStream,
          totalBytes = originalContent.length.toLong(),
          onProgress = { _, _, _ -> }
      )
      assertTrue("Upload must succeed", uploadResult.isSuccess)
      val remotePath = uploadResult.getOrThrow()
      assertTrue("Remote path must not be blank", remotePath.isNotBlank())

      // 2. Download actual file bytes from cloud storage as Admin
      val uploadedFileWithMeta = vaultFile.copy(
          isBackedUp = true,
          remoteStoragePath = remotePath
      )
      val downloadResult = com.example.firebase.FirebaseManager.downloadFileFromCloud(
          context = context,
          hostId = testHostId,
          file = uploadedFileWithMeta
      )
      assertTrue("Download must succeed: ${downloadResult.exceptionOrNull()?.message}", downloadResult.isSuccess)
      val downloadedPhysicalFile = downloadResult.getOrThrow()
      assertTrue(downloadedPhysicalFile.exists())

      // 3. Verify physical content matches original bytes exactly
      val downloadedText = downloadedPhysicalFile.readText()
      assertEquals("Downloaded physical content must match uploaded bytes exactly", originalContent, downloadedText)

      // Clean up
      downloadedPhysicalFile.delete()
    }
  }

  @Test
  fun `test storage node directory indexing and Android media naming`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Test folder display name parsing
    val mediaUriStr = "content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia"
    val mediaName = StorageUtils.getFolderDisplayName(context, mediaUriStr)
    assertEquals("Android/media", mediaName)

    val dcimUriStr = "content://com.android.externalstorage.documents/tree/primary%3ADCIM"
    val dcimName = StorageUtils.getFolderDisplayName(context, dcimUriStr)
    assertEquals("DCIM", dcimName)

    // Test directory structure scanning with file model verification
    val baseDir = java.io.File(context.cacheDir, "StorageNodeTest").apply { mkdirs() }
    val dcimDir = java.io.File(baseDir, "DCIM").apply { mkdirs() }
    val cameraDir = java.io.File(dcimDir, "Camera").apply { mkdirs() }
    java.io.File(cameraDir, "photo1.jpg").apply { writeText("photo_data") }

    val mediaDir = java.io.File(baseDir, "Android/media").apply { mkdirs() }
    java.io.File(mediaDir, "audio.opus").apply { writeText("audio_data") }

    val (files, summary) = StorageUtils.scanFolder(baseDir, "test_device_1", "node_folder_1")
    assertTrue(files.isNotEmpty())
    assertTrue(summary.foldersFound >= 3)

    val photoFile = files.firstOrNull { it.name == "photo1.jpg" }
    org.junit.Assert.assertNotNull(photoFile)
    assertEquals("photo1.jpg", photoFile!!.displayName)
    assertEquals("test_device_1", photoFile.deviceId)
    assertEquals("node_folder_1", photoFile.folderId)
    assertTrue("Parent folder ID should reflect hierarchy", photoFile.parentFolderId.contains("DCIM") || photoFile.parentFolderId.contains("Camera"))

    val audioFile = files.firstOrNull { it.name == "audio.opus" }
    org.junit.Assert.assertNotNull(audioFile)
    assertEquals("Android/media/audio.opus", audioFile!!.relativePath)
    assertEquals("node_folder_1", audioFile.folderId)
  }
}
