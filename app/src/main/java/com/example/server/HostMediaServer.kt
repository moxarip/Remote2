package com.example.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.models.VaultFile
import com.example.repository.BackupRepository
import com.example.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Lightweight, authenticated HTTP Media Streaming Server for the Host Storage Node.
 * Supports:
 * - HTTP Range requests (206 Partial Content) for true seeking and video streaming without downloading full files.
 * - Downsampled, lightweight image previews without saving permanent local copies.
 * - Direct authenticated file downloads over local Wi-Fi.
 * - Token authentication to guarantee only the paired Admin can stream or preview media.
 */
class HostMediaServer private constructor(private val context: Context) {

    private val threadPool = Executors.newCachedThreadPool()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    var port: Int = 9090
        private set

    var authToken: String = UUID.randomUUID().toString()
        private set

    var isRunning: Boolean = false
        private set

    companion object {
        private const val TAG = "HostMediaServer"
        private const val DEFAULT_PORT = 9090
        private const val CHUNK_SIZE = 64 * 1024 // 64 KB streaming buffer

        @Volatile
        private var instance: HostMediaServer? = null

        fun getInstance(context: Context): HostMediaServer {
            return instance ?: synchronized(this) {
                instance ?: HostMediaServer(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Start the media server on an available local port.
     */
    fun start(): Boolean {
        if (isRunning) return true

        return try {
            authToken = UUID.randomUUID().toString()
            var currentPort = DEFAULT_PORT
            var socket: ServerSocket? = null

            for (p in currentPort..(currentPort + 20)) {
                try {
                    socket = ServerSocket(p)
                    currentPort = p
                    break
                } catch (e: Exception) {
                    // Try next port if taken
                }
            }

            if (socket == null) {
                socket = ServerSocket(0) // bind to any open port
                currentPort = socket.localPort
            }

            serverSocket = socket
            port = currentPort
            isRunning = true
            Log.i(TAG, "HostMediaServer started on port $port (Token: $authToken)")

            serverJob = scope.launch {
                listenForClients()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HostMediaServer: ${e.message}", e)
            isRunning = false
            false
        }
    }

    /**
     * Stop the media server.
     */
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverJob?.cancel()
        Log.i(TAG, "HostMediaServer stopped")
    }

    /**
     * Returns the local Wi-Fi / LAN IP address of this Host phone.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (hostAddr.startsWith("192.168.") || hostAddr.startsWith("10.") || hostAddr.startsWith("172.")) {
                            return hostAddr
                        }
                    }
                }
            }
            // Fallback: any non-loopback IPv4
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving local IP: ${e.message}")
        }
        return null
    }

    /**
     * Returns full base URL for media streaming if running and IP is resolved.
     */
    fun getBaseStreamingUrl(): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$port"
    }

    private fun listenForClients() {
        val server = serverSocket ?: return
        while (isRunning && !server.isClosed) {
            try {
                val clientSocket = server.accept()
                threadPool.submit {
                    handleClient(clientSocket)
                }
            } catch (e: SocketException) {
                // Server socket closed
                break
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Client accept exception: ${e.message}")
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val requestHeaderLines = mutableListOf<String>()
            val reader = input.bufferedReader()
            var firstLine: String? = null

            // Read HTTP request line
            while (true) {
                val line = reader.readLine() ?: break
                if (firstLine == null) {
                    firstLine = line
                }
                if (line.isEmpty()) break
                requestHeaderLines.add(line)
            }

            if (firstLine.isNullOrBlank()) {
                socket.close()
                return
            }

            val parts = firstLine.split(" ")
            if (parts.size < 2) {
                sendError(output, 400, "Bad Request")
                socket.close()
                return
            }

            val method = parts[0].uppercase()
            val fullPath = parts[1]

            // Parse headers
            val headersMap = mutableMapOf<String, String>()
            for (headerLine in requestHeaderLines.drop(1)) {
                val colonIdx = headerLine.indexOf(':')
                if (colonIdx > 0) {
                    val k = headerLine.substring(0, colonIdx).trim().lowercase()
                    val v = headerLine.substring(colonIdx + 1).trim()
                    headersMap[k] = v
                }
            }

            // Parse query parameters
            val pathOnly = fullPath.substringBefore('?')
            val queryStr = fullPath.substringAfter('?', "")
            val queryParams = parseQueryParams(queryStr)

            // Security Validation: Token check
            val clientToken = queryParams["token"]
            if (clientToken.isNullOrBlank() || clientToken != authToken) {
                Log.w(TAG, "Unauthorized request for $fullPath from ${socket.inetAddress.hostAddress}")
                sendError(output, 403, "Forbidden - Invalid Auth Token")
                socket.close()
                return
            }

            val fileId = queryParams["fileId"]
            if (fileId.isNullOrBlank()) {
                sendError(output, 400, "Missing fileId parameter")
                socket.close()
                return
            }

            // Resolve file securely from Host indexed files
            val repository = BackupRepository.getInstance(context)
            val file = repository.hostFiles.value.firstOrNull { it.fileId == fileId }
            if (file == null) {
                sendError(output, 404, "File Not Found")
                socket.close()
                return
            }

            when (pathOnly) {
                "/stream" -> {
                    handleStreamRequest(output, file, headersMap, method == "HEAD")
                }
                "/preview" -> {
                    handlePreviewRequest(output, file, method == "HEAD")
                }
                "/download" -> {
                    handleDownloadRequest(output, file, method == "HEAD")
                }
                else -> {
                    sendError(output, 404, "Not Found")
                }
            }
        } catch (e: SocketException) {
            // Client closed stream (e.g. user seeked or paused) - normal streaming behavior
            Log.d(TAG, "Socket closed by client during streaming: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client request: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun handleStreamRequest(
        output: OutputStream,
        file: VaultFile,
        headers: Map<String, String>,
        isHead: Boolean
    ) {
        val repository = BackupRepository.getInstance(context)
        val (stream, totalBytes) = StorageUtils.openInputStreamForVaultFile(context, file, repository.sharedFolders.value)
        if (stream == null || totalBytes <= 0) {
            sendError(output, 404, "File stream could not be opened")
            return
        }

        val rangeHeader = headers["range"]
        val mimeType = if (file.mimeType.isNotBlank() && file.mimeType != "*/*") {
            file.mimeType
        } else {
            StorageUtils.getMimeTypeFromExtension(file.name)
        }

        try {
            if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
                // Handle HTTP 206 Range Request
                val rangeSpec = rangeHeader.substring(6).trim()
                val rangeParts = rangeSpec.split("-")
                val start = rangeParts[0].toLongOrNull() ?: 0L
                val end = if (rangeParts.size > 1 && rangeParts[1].isNotBlank()) {
                    rangeParts[1].toLongOrNull() ?: (totalBytes - 1)
                } else {
                    totalBytes - 1
                }.coerceAtMost(totalBytes - 1)

                val contentLength = (end - start + 1).coerceAtLeast(0L)

                val headerStr = StringBuilder()
                    .append("HTTP/1.1 206 Partial Content\r\n")
                    .append("Content-Type: $mimeType\r\n")
                    .append("Content-Range: bytes $start-$end/$totalBytes\r\n")
                    .append("Content-Length: $contentLength\r\n")
                    .append("Accept-Ranges: bytes\r\n")
                    .append("Connection: keep-alive\r\n")
                    .append("Access-Control-Allow-Origin: *\r\n\r\n")
                    .toString()

                output.write(headerStr.toByteArray(Charsets.UTF_8))
                output.flush()

                if (!isHead && contentLength > 0) {
                    skipBytesFully(stream, start)
                    streamChunked(stream, output, contentLength)
                }
            } else {
                // Handle standard HTTP 200 OK
                val headerStr = StringBuilder()
                    .append("HTTP/1.1 200 OK\r\n")
                    .append("Content-Type: $mimeType\r\n")
                    .append("Content-Length: $totalBytes\r\n")
                    .append("Accept-Ranges: bytes\r\n")
                    .append("Connection: keep-alive\r\n")
                    .append("Access-Control-Allow-Origin: *\r\n\r\n")
                    .toString()

                output.write(headerStr.toByteArray(Charsets.UTF_8))
                output.flush()

                if (!isHead) {
                    streamChunked(stream, output, totalBytes)
                }
            }
        } finally {
            try { stream.close() } catch (e: Exception) {}
        }
    }

    private fun handlePreviewRequest(
        output: OutputStream,
        file: VaultFile,
        isHead: Boolean
    ) {
        val repository = BackupRepository.getInstance(context)
        val (stream, totalBytes) = StorageUtils.openInputStreamForVaultFile(context, file, repository.sharedFolders.value)
        if (stream == null) {
            sendError(output, 404, "File preview not found")
            return
        }

        try {
            val isImage = file.mimeType.startsWith("image/") ||
                    StorageUtils.getCategoryForFile(file.name, file.mimeType) == "Photos"

            if (isImage) {
                // Downsample image for high-speed, lightweight preview
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val buffered = BufferedInputStream(stream)
                buffered.mark(1024 * 1024)
                BitmapFactory.decodeStream(buffered, null, options)
                buffered.reset()

                // Calculate downsampling to max 1280px dimension
                val maxDim = 1280
                var sampleSize = 1
                if (options.outHeight > maxDim || options.outWidth > maxDim) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / sampleSize) >= maxDim && (halfWidth / sampleSize) >= maxDim) {
                        sampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.RGB_565 // memory efficient
                }

                val bitmap = BitmapFactory.decodeStream(buffered, null, decodeOptions)
                if (bitmap != null) {
                    val byteStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteStream)
                    bitmap.recycle()
                    val previewBytes = byteStream.toByteArray()

                    val headerStr = StringBuilder()
                        .append("HTTP/1.1 200 OK\r\n")
                        .append("Content-Type: image/jpeg\r\n")
                        .append("Content-Length: ${previewBytes.size}\r\n")
                        .append("Cache-Control: public, max-age=3600\r\n")
                        .append("Access-Control-Allow-Origin: *\r\n\r\n")
                        .toString()

                    output.write(headerStr.toByteArray(Charsets.UTF_8))
                    if (!isHead) {
                        output.write(previewBytes)
                    }
                    output.flush()
                    return
                }
            }

            // Fallback: stream original stream if downsampling not applicable
            val mimeType = if (file.mimeType.isNotBlank() && file.mimeType != "*/*") file.mimeType else "application/octet-stream"
            val headerStr = StringBuilder()
                .append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: $mimeType\r\n")
                .append("Content-Length: $totalBytes\r\n")
                .append("Access-Control-Allow-Origin: *\r\n\r\n")
                .toString()

            output.write(headerStr.toByteArray(Charsets.UTF_8))
            output.flush()
            if (!isHead) {
                streamChunked(stream, output, totalBytes)
            }
        } finally {
            try { stream.close() } catch (e: Exception) {}
        }
    }

    private fun handleDownloadRequest(
        output: OutputStream,
        file: VaultFile,
        isHead: Boolean
    ) {
        val repository = BackupRepository.getInstance(context)
        val (stream, totalBytes) = StorageUtils.openInputStreamForVaultFile(context, file, repository.sharedFolders.value)
        if (stream == null) {
            sendError(output, 404, "File not available for download")
            return
        }

        try {
            val mimeType = if (file.mimeType.isNotBlank() && file.mimeType != "*/*") file.mimeType else "application/octet-stream"
            val headerStr = StringBuilder()
                .append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: $mimeType\r\n")
                .append("Content-Length: $totalBytes\r\n")
                .append("Content-Disposition: attachment; filename=\"${file.name}\"\r\n")
                .append("Accept-Ranges: bytes\r\n")
                .append("Access-Control-Allow-Origin: *\r\n\r\n")
                .toString()

            output.write(headerStr.toByteArray(Charsets.UTF_8))
            output.flush()

            if (!isHead) {
                streamChunked(stream, output, totalBytes)
            }
        } finally {
            try { stream.close() } catch (e: Exception) {}
        }
    }

    private fun streamChunked(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(CHUNK_SIZE)
        var remaining = maxBytes
        while (remaining > 0) {
            val toRead = remaining.coerceAtMost(CHUNK_SIZE.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        output.flush()
    }

    private fun skipBytesFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) break
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun sendError(output: OutputStream, statusCode: Int, statusText: String) {
        val body = "{\"error\": \"$statusText\", \"status\": $statusCode}"
        val resp = StringBuilder()
            .append("HTTP/1.1 $statusCode $statusText\r\n")
            .append("Content-Type: application/json\r\n")
            .append("Content-Length: ${body.length}\r\n")
            .append("Connection: close\r\n")
            .append("Access-Control-Allow-Origin: *\r\n\r\n")
            .append(body)
            .toString()
        output.write(resp.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (query.isBlank()) return map
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }
}
