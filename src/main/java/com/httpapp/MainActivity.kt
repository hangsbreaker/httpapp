package com.httpapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import sun.security.krb5.internal.HostAddress
import java.io.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.Executors
import android.text.format.Formatter
import android.text.util.Linkify


class MainActivity : AppCompatActivity() {
    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
    }
    private var serverUp = false
    var dir = "/";
    private val TAG = "Permission"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var permis = checkPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                STORAGE_PERMISSION_CODE)

        if(permis) {
            //println("Create folder")
            val path = this.getExternalFilesDir(null)
            //val path = this.filesDir
            val folder = File(path, "www")
            if (!folder.exists()) {
                folder.mkdirs()
                println(folder.exists()) // u'll get true
            }else{
                //println("Folder already")
            }

        }

        val port = 1111

        serverButton.setOnClickListener {
            serverUp = if(!serverUp){
                startServer(port)
                true
            } else{
                stopServer()
                false
            }

        }
    }

    // Custom method to determine whether a service is running
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Loop through the running services
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                // If the service is running then return true
                return true
            }
        }
        return false
    }

    fun streamToString(inputStream: InputStream): String {
        val s = Scanner(inputStream).useDelimiter("\\A")
        var res = ""
        if (s.hasNext()) {
            res = s.next()
        }
        return res;
    }

    private fun sendResponse(httpExchange: HttpExchange, responseText: String){
        httpExchange.sendResponseHeaders(200, responseText.length.toLong())
        val os = httpExchange.responseBody
        os.write(responseText.toByteArray())
        os.close()
    }

    private var mHttpServer: HttpServer? = null

    private fun startServer(port: Int){
        dir = "/";
        val PACKAGE_NAME = getApplicationContext().getPackageName();
        try{
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress: String = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)

            mHttpServer = HttpServer.create(InetSocketAddress(port), 0)
            mHttpServer!!.executor = Executors.newCachedThreadPool()
            //mHttpServer!!.createContext("/", rootHandler)
            // 'this' refers to the handle method
            mHttpServer!!.createContext(dir, fileHandler)
            mHttpServer!!.start()//start server;
            println("Server is running on ${mHttpServer!!.address}:$port _ IP Address: $ipAddress")
            serverTextView.text = "Server is running\nIP Address: $ipAddress:$port\n" +
                    "Place File in Android > data > $PACKAGE_NAME > files > www"
            Linkify.addLinks(serverTextView, Linkify.WEB_URLS)
            serverButton.text = getString(R.string.stop_server)
        } catch (e: IOException){
            e.printStackTrace();
        }
    }

    private fun stopServer() {
        if (mHttpServer != null){
            mHttpServer!!.stop(0)
            mHttpServer = null
            serverTextView.text = "Server is down"
            serverButton.text = getString(R.string.start_server)
        }
    }

    // Handler for root endpoint
    private val rootHandler = HttpHandler { exchange ->
        run {
            // Get request method
            when (exchange!!.requestMethod) {
                "GET" -> {
                    sendResponse(exchange, "Welcome to my server")
                }
            }
        }
    }

    // Handler for root endpoint
    private val fileHandler = HttpHandler { exchange ->
        run {
            // Get request method
            dir = exchange.requestURI.toString();

            val path = this.getExternalFilesDir(null)
            var inputString = ""

            if(!dir.equals("/")) {
                val folder = File(path, "www")
                if (!folder.exists()) {
                    folder.mkdirs()
                    //println("Folder not already")
                } else {
                    val file = File(path.toString() + "/www", dir)
                    inputString = FileInputStream(file).bufferedReader().use { it.readText() }
                    //println(inputString);
                }
            }

            if(inputString.equals("")){
                val PACKAGE_NAME = getApplicationContext().getPackageName();
                inputString = "Hello World."
            }
            //================
            when (exchange!!.requestMethod) {
                "GET" -> {
                    sendResponse(exchange, inputString)
                }
            }
        }
    }

    private val messageHandler = HttpHandler { httpExchange ->
        run {
            when (httpExchange!!.requestMethod) {
                "GET" -> {
                    // Get all messages
                    sendResponse(httpExchange, "Would be all messages stringified json")
                }
                "POST" -> {
                    val inputStream = httpExchange.requestBody

                    val requestBody = streamToString(inputStream)
                    val jsonBody = JSONObject(requestBody)
                    // save message to database

                    //for testing
                    sendResponse(httpExchange, jsonBody.toString())

                }

            }
        }
    }

    private fun checkPermission(permission: String, requestCode: Int) : Boolean{
        if (ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(permission), requestCode)
            return true
        } else {
            //Toast.makeText(this@MainActivity, "Permission already granted", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        /*if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this@MainActivity, "Camera Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Camera Permission Denied", Toast.LENGTH_SHORT).show()
            }
        } else */
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this@MainActivity, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
