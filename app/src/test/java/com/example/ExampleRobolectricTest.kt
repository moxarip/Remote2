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
}
