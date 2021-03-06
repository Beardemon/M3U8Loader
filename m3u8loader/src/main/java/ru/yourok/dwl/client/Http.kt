package ru.yourok.dwl.client

import android.net.Uri
import ru.yourok.dwl.settings.Settings
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.HttpURLConnection.*
import java.net.URL
import java.util.zip.GZIPInputStream


/**
 * Created by yourok on 07.11.17.
 */

class Http(url: Uri) : Client {
    private var currUrl: String = url.toString()
    private var isConn: Boolean = false
    private var connection: HttpURLConnection? = null
    private var errMsg: String = ""
    private var inputStream: InputStream? = null

    override fun connect() {
        connect(0)
    }

    override fun connect(pos: Long): Long {
        var responseCode: Int
        var redirCount = 0
        do {
            var url = URL(currUrl)
            connection = url.openConnection() as HttpURLConnection
            connection!!.connectTimeout = 30000
            connection!!.readTimeout = 15000
            connection!!.setRequestMethod("GET")
            connection!!.setDoInput(true)

            connection!!.setRequestProperty("UserAgent", "DWL/1.1.0 (Android)")
            connection!!.setRequestProperty("Accept", "*/*")
            connection!!.setRequestProperty("Accept-Encoding", "gzip")
            if (pos > 0)
                connection!!.setRequestProperty("Range", "bytes=$pos-")

            if (Settings.headers.isNotEmpty()) {
                Settings.headers.forEach { (k, v) ->
                    connection!!.setRequestProperty(k, v)
                }
            }

            connection!!.connect()

            responseCode = connection!!.getResponseCode()
            val redirected = responseCode == HTTP_MOVED_PERM || responseCode == HTTP_MOVED_TEMP || responseCode == HTTP_SEE_OTHER
            if (redirected) {
                currUrl = connection!!.getHeaderField("Location")
                connection!!.disconnect()
                redirCount++
            }
            if (redirCount > 5) {
                throw IOException("Error connect to: " + currUrl + " too many redirects")
            }
        } while (redirected)


        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw IOException("Error connect to: " + currUrl + " " + connection!!.responseMessage)
        }
        isConn = true
        if ((connection!!.getHeaderField("Accept-Ranges")?.toLowerCase() ?: "") == "none")
            return -1
        return getSize()
    }

    override fun isConnected(): Boolean {
        return isConn
    }

    override fun getSize(): Long {
        if (!isConn)
            return 0

        var cl = connection!!.getHeaderField("Content-Range")
        try {
            if (!cl.isNullOrEmpty()) {
                val cr = cl.split("/")
                if (cr.isNotEmpty())
                    cl = cr.last()
                return cl.toLong()
            }
        } catch (e: Exception) {
        }

        cl = connection!!.getHeaderField("Content-Length")
        try {
            if (!cl.isNullOrEmpty()) {
                return cl.toLong()
            }
        } catch (e: Exception) {
        }

        return 0
    }

    override fun getUrl(): String {
        return currUrl
    }

    override fun getInputStream(): InputStream? {
        if (inputStream == null && connection != null) {
            if ("gzip".equals(connection?.getContentEncoding()))
                inputStream = GZIPInputStream(connection!!.getInputStream())
            else
                inputStream = connection!!.getInputStream()
        }

        return inputStream
    }

    override fun read(b: ByteArray): Int {
        if (!isConn or (getInputStream() == null))
            throw IOException("connect before read")
        var sz = getInputStream()!!.read(b)
        var size = sz
        while (sz > 0 && sz < b.size / 2) {
            try {
                sz = getInputStream()!!.read(b, size, b.size - size)
                if (sz > 0)
                    size += sz
                else
                    break
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }
        return size
    }

    override fun getErrorMessage(): String {
        return errMsg
    }

    override fun close() {
        try {
            inputStream?.close()
        } catch (e: Exception) {
        }
        connection?.disconnect()
        isConn = false
    }
}