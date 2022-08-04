package com.httpapp

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.EXTRA_INITIAL_URI
import android.provider.MediaStore
import android.text.InputType
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.Executors
import android.text.format.Formatter
import android.text.util.Linkify
import android.util.Base64
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import java.net.URLDecoder


class MainActivity : AppCompatActivity() {
    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
    }

    private var serverUp = false
    var port = 1111

    var dir = "/"
    var rootpath = ""
    private val TAG = "Permission"
    private val DIR_CODE = 99

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var permis = checkPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                STORAGE_PERMISSION_CODE)
        //val PACKAGE_NAME = getApplicationContext().getPackageName();

        loadData()

        if(permis) {
            //println("Create folder")
            val path = this.getExternalFilesDir(null)
            //val path = this.filesDir
            val folder = File(path, "www")
            //val folder = File(rootpath)
            if (!folder.exists()) {
                folder.mkdirs()
                rootpath = path.toString()+"/www"
                println("Create Folder "+path.toString()+"/www")
                println(folder.exists()) // u'll get true
            }else{
                if(rootpath.equals("")){
                    rootpath = path.toString()+"/www"
                }
                println("Folder already")
            }

        }


        txtPort.text = "Port: "+port
        txtPath.text = "Path: "+rootpath

        txtPort.setOnClickListener {
            showdialog()
        }
        txtPath.setOnClickListener {
            openDirectory(EXTRA_INITIAL_URI)
        }

        serverButton.setOnClickListener {
            serverUp = if(!serverUp){
                startServer(port)
                true
            } else{
                stopServer()
                false
            }
        }

        resetButton.setOnClickListener {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setMessage("Reset configuration?")
                .setCancelable(false)
                .setPositiveButton("Yes") { dialog, id ->
                    val path = this.getExternalFilesDir(null)
                    dir = "/"
                    port = 1111
                    rootpath = path.toString()+"/www"

                    txtPort.text = "Port: "+port
                    txtPath.text = "Path: "+rootpath

                    saveData()
                }
                .setNegativeButton("No") { dialog, id ->
                    // Dismiss the dialog
                    dialog.dismiss()
                }
            val alert = builder.create()
            alert.show()
        }
    }
    private fun saveData(){
        val inPort = port
        val txPath = rootpath

        val sharedPreferences = getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.apply {
            putInt("PORT", inPort)
            putString("PATH",txPath)
        }.apply()
    }
    private fun loadData(){
        val sharedPreferences = getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        val inPort = sharedPreferences.getInt("PORT",port)
        val txPath = sharedPreferences.getString("PATH",rootpath)

        port = inPort
        rootpath = txPath.toString()
        txtPort.text = "Port: "+port
        txtPath.text = "Path: "+rootpath
    }

    fun showdialog(){
        val builder: AlertDialog.Builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Port")

        val container = LinearLayout(this)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(45,0,45,0)
        // Set up the input
        val input = EditText(this)
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setHint("Enter Port Number")
        input.inputType = InputType.TYPE_CLASS_NUMBER
        container.addView(input,lp)

        builder.setView(container)

        // Set up the buttons
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
            // Here you get get input text from the Edittext
            port = input.text.toString().toInt()
            txtPort.text = "Port: "+port

            saveData()
        })
        builder.setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, which -> dialog.cancel() })

        builder.show()
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

    private var mHttpServer: HttpServer? = null

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun startServer(port: Int){
        dir = "/";
        try{
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            var ipAddress: String = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
            if(ipAddress.equals("0.0.0.0")){
                ipAddress = "127.0.0.1"
            }

            mHttpServer = HttpServer.create(InetSocketAddress(port), 0)
            mHttpServer!!.executor = Executors.newCachedThreadPool()
            //mHttpServer!!.createContext("/", rootHandler)
            // 'this' refers to the handle method
            mHttpServer!!.createContext(dir, fileHandler)
            mHttpServer!!.start()//start server;
            println("Server is running on ${mHttpServer!!.address}:$port _ IP Address: $ipAddress")
            serverTextView.text = "Server is running\nIP Address: $ipAddress:$port\n" +
                    "Place File in "+rootpath
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

    private fun sendResponse(httpExchange: HttpExchange, responseText: String){
        val os = httpExchange.responseBody
        if(responseText.equals("")) {
            var inputStream = ""
            if (dir.equals("/")) {
                httpExchange.sendResponseHeaders(200, inputStream.length.toLong())
                os.write(inputStream.toByteArray())
            } else {
                dir = dir.replace("/","")
                var file = File(rootpath,dir)
                //println("Send response: "+rootpath+"/"+dir)
                if (file.exists()) {
                    httpExchange.sendResponseHeaders(200, file.length())
                    os.write(file.readBytes(), 0, file.readBytes().size)
                } else {
                    inputStream = "404 Not Found"
                    httpExchange.sendResponseHeaders(404, inputStream.length.toLong())
                    os.write(inputStream.toByteArray())
                }
            }
        }else{
            httpExchange.sendResponseHeaders(200, responseText.length.toLong())
            os.write(responseText.toByteArray())
        }
        os.close()
    }

    // Handler for root endpoint
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private val fileHandler = HttpHandler { httpExchange ->
        run {
            // Get request method
            dir = URLDecoder.decode(httpExchange.requestURI.toString(), "UTF-8")
            //println("File Handler: "+dir.toString())
            sendResponse(httpExchange,"")
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
                //Toast.makeText(this@MainActivity, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openDirectory(pickerInitialUri: String) {
        // Choose a directory using the system's file picker.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // Provide read access to files and sub-directories in the user-selected
            // directory.
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

            // Optionally, specify a URI for the directory that should be opened in
            // the system file picker when it loads.
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }

        startActivityForResult(intent, DIR_CODE)
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onActivityResult(
        requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == DIR_CODE && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            resultData?.data?.also { uri ->
                val contentResolver = applicationContext.contentResolver

                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                // Check for the freshest data.
                contentResolver.run {
                    // Check for the freshest data.
                    takePersistableUriPermission(uri, takeFlags)
                }
                rootpath = uri.path.toString()
                if(rootpath.contains("primary")){
                    // /tree/primary:www/
                    rootpath = rootpath.replace("/tree/primary:", "/sdcard/")
                }else {
                    // /tree/3661-3231:Download/
                    rootpath = rootpath.replace("/tree", "").replace(":","/")
                }
                //Toast.makeText(this@MainActivity, ""+rootpath, Toast.LENGTH_SHORT).show()
                txtPath.text = "Path: "+rootpath

                saveData()
                // Perform operations on the document using its URI.
            }
        }
    }
}
