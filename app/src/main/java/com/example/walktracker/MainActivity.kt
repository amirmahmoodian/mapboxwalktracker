package com.example.walktracker

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider.getUriForFile
import com.example.walktracker.databinding.ActivityMainBinding
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.viewannotation.ViewAnnotationManager
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import java.io.*
import java.lang.ref.WeakReference
import kotlin.math.roundToInt


var mapView: MapView? = null

class MainActivity : AppCompatActivity(), SensorEventListener {

    lateinit var stepSensor:Sensor

    private var sensorManager: SensorManager? = null

    private var running = false

    private var totalSteps = 0f

    private var previousTotalSteps = 0f

    private val filename = "SampleFile.geojason"

    private val filepath = "MyFileStorage"

    var myExternalFile: File? = null

    companion object {
        fun isExternalStorageReadOnly(): Boolean {
            val extStorageState = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED_READ_ONLY == extStorageState
        }
        fun isExternalStorageAvailable(): Boolean {
            val extStorageState = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED == extStorageState
        }
        private const val GEOJSON_SOURCE_ID = "line"
        private const val LATITUDE = 51.36489
        private const val LONGITUDE = 35.69551
        private const val ZOOM = 14.0
    }

    var text:String = " {\n" +
            "   \"type\": \"FeatureCollection\",\n" +
            "   \"features\": [\n" +
            "     {\n" +
            "       \"type\": \"Feature\",\n" +
            "       \"properties\": {\n" +
            "         \"name\": \"Crema to Council Crest\"\n" +
            "       },\n" +
            "       \"geometry\": {\n" +
            "         \"type\": \"LineString\",\n" +
            "         \"coordinates\": [\n" +
            "         [51.36512, 35.6953],\n" +
            "         [51.36499, 36.69552]  ]}}]}"

    var myline :String="{\n" +
            "  \"type\": \"FeatureCollection\",\n" +
            "  \"features\": [\n" +
            "    {\n" +
            "      \"type\": \"Feature\",\n" +
            "      \"properties\": {\n" +
            "        \"name\": \"Crema to Council Crest\"\n" +
            "      },\n" +
            "      \"geometry\": {\n" +
            "        \"type\": \"LineString\",\n" +
            "        \"coordinates\": ["

    var flag:Boolean = false

    private var distance:Double = 0.0

    private lateinit var locationPermissionHelper: LocationPermissionHelper

    private lateinit var binding: ActivityMainBinding

    private lateinit var viewAnnotationManager: ViewAnnotationManager

    private val addedWaypoints = WaypointsSet()

    private lateinit var mapboxMap: MapboxMap

    private val navigationLocationProvider = NavigationLocationProvider()

    private lateinit var mapboxNavigation: MapboxNavigation

    private val locationObserver = object : LocationObserver {
        var firstLocationUpdateReceived = false

        override fun onNewRawLocation(rawLocation: Location) {
            // not handled
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            // update location puck's position on the map
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )
            if (!firstLocationUpdateReceived){
                myline += "[" + ((enhancedLocation.longitude * 100000.0).roundToInt() / 100000.0) + "," + " " + ((enhancedLocation.latitude * 100000.0).roundToInt() / 100000.0) + "]"
        }
            else{
                var we = myline.substring(myline.length - 5, myline.length)
                var we2 = myline
                if (myline.substring(myline.length - 5, myline.length)=="]}}]}") {

                        myline = myline.substring(0, myline.length - 6)
                        println(myline)
                        println("############################")

                }
                myline += ",[" + ((enhancedLocation.longitude * 100000.0).roundToInt() / 100000.0)+
                        "," + " " +
                        ((enhancedLocation.latitude * 100000.0).roundToInt() / 100000.0) + "]  "
            }

            if (!firstLocationUpdateReceived) {
                firstLocationUpdateReceived = true
                moveCameraTo(enhancedLocation)
            }

            binding.start.apply {
                setOnClickListener {
                    flag = true
                    binding.start.visibility = View.INVISIBLE
                    binding.stop.apply {
                        if(flag) {
                            binding.stop.visibility = View.VISIBLE
                        }
                        setOnClickListener {
                            flag = false
                            binding.stop.visibility = View.INVISIBLE
                            binding.window.apply {
                                binding.window.visibility = View.VISIBLE
                                binding.multipleWaypointResetRouteButton.apply {
                                    setOnClickListener {
                                        binding.window.visibility = View.INVISIBLE
                                        binding.start.visibility = View.VISIBLE
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (flag){
                addWaypoint(Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude))
            }
        }

        private fun moveCameraTo(location: Location) {
            val mapAnimationOptions = MapAnimationOptions.Builder().duration(0).build()
            binding.mapView.camera.easeTo(
                CameraOptions.Builder()
                    // Centers the camera to the lng/lat specified.
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    // specifies the zoom value. Increase or decrease to zoom in or zoom out
                    .zoom(16.0)
                    .build(),
                mapAnimationOptions
            )
        }
    }

    private fun addWaypoint(destination: Point) {
        val originLocation = navigationLocationProvider.lastLocation
        val originPoint = originLocation?.let {
            Point.fromLngLat(it.longitude, it.latitude)
        } ?: return
        if (addedWaypoints.isEmpty) {
            addedWaypoints.addRegular(destination)
        }
        if(addedWaypoints.coordinatesList().size>1) {
            if (destination !=
                addedWaypoints.coordinatesList()[addedWaypoints.coordinatesList().size - 1]
            ) {
                addedWaypoints.addRegular(destination)
                addAnnotationToMap(originPoint,3)
            }
        }
        else {
            addedWaypoints.addRegular(destination)
        }
        distance += TurfMeasurement.distance(
            addedWaypoints.coordinatesList()[addedWaypoints.coordinatesList().size - 2],
            addedWaypoints.coordinatesList()[addedWaypoints.coordinatesList().size - 1],
            TurfConstants.UNIT_KILOMETERS
        )
        val distance = ((distance * 100.0).roundToInt() / 100.0).toBigDecimal().toPlainString()
        binding.dis.text = distance
    }

    private fun addAnnotationToMap(destination: Point,type:Int) {

        var mydot = R.drawable.dot
        when (type) {
            1 -> mydot=R.drawable.startdot
            2 -> mydot=R.drawable.enddot
            3 -> mydot=R.drawable.dot
            else -> { // Note the block
                print("x is neither 1 nor 2 nor 3")
            }
        }

        // Create an instance of the Annotation API and get the PointAnnotationManager.
        bitmapFromDrawableRes(
            this@MainActivity,
            mydot
        )?.let {
            val annotationApi = binding.mapView?.annotations
            val pointAnnotationManager = annotationApi?.createPointAnnotationManager(binding.mapView!!)
            // Set options for the resulting symbol layer.
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
                // Define a geographic coordinate.
                .withPoint(destination)

                // Specify the bitmap you assigned to the point annotation
                // The bitmap will be added to map style automatically.
                .withIconImage(it)
            // Add the resulting pointAnnotation to the map.
            pointAnnotationManager?.create(pointAnnotationOptions)

        }
    }

    private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
        convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))

    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
            // copying drawable object to not manipulate on the same reference
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap: Bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadData()
        resetSteps()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        viewAnnotationManager = binding.mapView.viewAnnotationManager

        mapboxMap = binding.mapView.getMapboxMap()

        binding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            enabled = true
        }

        mapboxNavigation = MapboxNavigationProvider.create(
            NavigationOptions.Builder(this.applicationContext)
                .accessToken(getString(R.string.mapbox_access_token))
                .build()
        )

        locationPermissionHelper = LocationPermissionHelper(WeakReference(this))
        locationPermissionHelper.checkPermissions {
            mapboxNavigation.startTripSession(withForegroundService = false)
        }

        mapboxMap.loadStyleUri(
            Style.MAPBOX_STREETS
        ) {

        }
        binding.start.visibility = View.VISIBLE

        if (!isExternalStorageAvailable() || isExternalStorageReadOnly()) {
            Toast.makeText(this,"wowowowow",Toast.LENGTH_SHORT).show()
        } else {
            myExternalFile = File(getExternalFilesDir(filepath), filename)
        }

    }

    override fun onStart() {
        super.onStart()
        mapboxNavigation.registerLocationObserver(locationObserver)
    }

    override fun onStop() {
        super.onStop()
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        sensorManager?.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        running = true

        // Returns the number of steps taken by the user since the last reboot while activated
        // This sensor requires permission android.permission.ACTIVITY_RECOGNITION.
        // So don't forget to add the following permission in AndroidManifest.xml present in manifest folder of the app.
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!!


        if (stepSensor == null) {
            // This will give a toast message to the user if there is no sensor in the device
            Toast.makeText(this, "No sensor detected on this device", Toast.LENGTH_SHORT).show()
        } else {
            // Rate suitable for the user interface
            sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {

        // Calling the TextView that we made in activity_main.xml
        // by the id given to that TextView
        var tv_stepsTaken = findViewById<TextView>(R.id.stepsnumber)

        if (running) {
            totalSteps = event!!.values[0]

            // Current steps are calculated by taking the difference of total steps
            // and previous steps
            val currentSteps = totalSteps.toInt() - previousTotalSteps.toInt()

            // It will show the current steps to the user
            tv_stepsTaken.text = ("$currentSteps")
        }
    }

    fun resetSteps() {
        var tv_stepsTaken = findViewById<TextView>(R.id.stepsnumber)
        var distance = findViewById<TextView>(R.id.dis)
        var resetbut = findViewById<TextView>(R.id.resetbut)
        resetbut.setOnClickListener {
            Toast.makeText(this, "Long tap to reset", Toast.LENGTH_SHORT).show()
        }

        resetbut.setOnLongClickListener {
            previousTotalSteps = totalSteps
            tv_stepsTaken.text = 0.toString()
            saveData()
            distance.text = 0.toString()
            this.distance =0.0
            true
        }
    }

    private fun saveData() {

        // Shared Preferences will allow us to save
        // and retrieve data in the form of key,value pair.
        // In this function we will save data
        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)

        val editor = sharedPreferences.edit()
        editor.putFloat("key1", previousTotalSteps)
        editor.apply()
    }

    private fun loadData() {

        // In this function we will retrieve data
        val sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val savedNumber = sharedPreferences.getFloat("key1", 0f)

        // Log.d is used for debugging purposes
        Log.d("MainActivity", "$savedNumber")

        previousTotalSteps = savedNumber
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // We do not have to write anything in this function for this app
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun writeToFile(data: String, context: Context) {
        try {
            val outputStreamWriter =
                OutputStreamWriter(context.openFileOutput( "from_crema_to_council_crest.geojson", MODE_PRIVATE))
            outputStreamWriter.write(data)

            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: " + e.toString())
        }
    }//toDo

    fun red (){
        try {
            val fos: FileOutputStream = FileOutputStream(myExternalFile)
            fos.write(text.toByteArray())
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }//toDo

    private fun readFromFile(context: Context): String? {
        var ret = ""
        try {
            val inputStream: InputStream? = context.openFileInput( "xxyyxx.geojson")
            if (inputStream != null) {
                val inputStreamReader = InputStreamReader(inputStream)
                val bufferedReader = BufferedReader(inputStreamReader)
                var receiveString: String? = ""
                val stringBuilder = StringBuilder()
                while (bufferedReader.readLine().also { receiveString = it } != null) {
                    stringBuilder.append("\n").append(receiveString)
                }
                inputStream.close()
                ret = stringBuilder.toString()
            }
        } catch (e: FileNotFoundException) {
            Log.e("login activity", "File not found: " + e.toString())
        } catch (e: IOException) {
            Log.e("login activity", "Can not read file: $e")
        }
        return ret
    }//toDo

    private fun liner(){
        val file = File("asxsets","from_crema_to_council_crest.geojson")
        myline += "]}}]}"
        println(myline)
        println("****************************")

        writeToFile(myline,this)

        var aa = readFromFile(this)

        println(aa)
        println("*$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$")

        var files: Array<String> = this.fileList()
        this.getDir("sampledata", Context.MODE_PRIVATE)
//        val file = File("asxsets","from_crema_to_council_crest.geojson")
        print(this.getDir("sampledata", Context.MODE_PRIVATE))
        println("&&&&&&&&&&&&&&&&")
//        red()
        println("^^^^^^^^^^^^")
//        val contentUri: Uri ?=
//            myExternalFile?.let { getUriForFile(this, "com.example.walktracke", it) }
//        println(contentUri)
//        println("^^^^^^^^^^^^")


        mapboxMap.loadStyle(
            (
                    style(styleUri = Style.MAPBOX_STREETS) {
                        +geoJsonSource(GEOJSON_SOURCE_ID){

                        }

                        +lineLayer("linelayer", GEOJSON_SOURCE_ID) {
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                            lineOpacity(0.7)
                            lineWidth(8.0)
                            lineColor("#888")
                        }
                    }
                    )
        )

    } //toDo

    fun provide() {
        val content = text
        val file = File(filesDir, "xxyyxx.geojson")
        if (!writeFile(file, content)) {
            return
        }
        val uri = getUriForFile(this, "com.example.provide", file)
        println(uri)
        println("****!!!!!!!!!!!!!!!****")

//        Intent intent = new Intent().setClassName("com.demo.filereceiver", "com.demo.filereceiver.MainActivity");
//        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        val clipData = ClipData(
            ClipDescription("Meshes", arrayOf(ClipDescription.MIMETYPE_TEXT_URILIST)),
            ClipData.Item(uri)
        )
//        intent.setClipData(clipData);
//        startActivity(intent);
    }//toDo

    private fun writeFile(file: File, content: String): Boolean {
        var stream: FileOutputStream? = null
        try {
            if (!file.exists()) {
                val created = file.createNewFile()
                if (!created) {
                    return false
                }
            }
            stream = FileOutputStream(file)
            stream.write(content.toByteArray())
            stream.flush()
            stream.close()
            return true
        } catch (e: IOException) {
            Log.e("provider", "IOException writing file: ", e)
        } finally {
            try {
                stream?.close()
            } catch (e: IOException) {
                Log.e("provider", "IOException closing stream: ", e)
            }
        }
        return false
    }//toDo

}

class WaypointsSet {

    private val waypoints = mutableListOf<Waypoint>()

    val isEmpty get() = waypoints.isEmpty()

    fun addNamed(point: Point, name: String) {
        waypoints.add(Waypoint(point, WaypointType.Named(name)))
    }

    fun addRegular(point: Point) {
        waypoints.add(Waypoint(point, WaypointType.Regular))
    }

    fun addSilent(point: Point) {
        waypoints.add(Waypoint(point, WaypointType.Silent))
    }

    fun clear() {
        waypoints.clear()
    }

    /***
     * Silent waypoint isn't really a waypoint.
     * It's just a coordinate that used to build a route.
     * That's why to make a waypoint silent we exclude its index from the waypointsIndices.
     */
    fun waypointsIndices(): List<Int> {
        return waypoints.mapIndexedNotNull { index, _ ->
            if (waypoints.isSilentWaypoint(index)) {
                null
            } else index
        }
    }

    /**
     * Returns names for added waypoints.
     * Silent waypoint can't have a name unless they're converted to regular because of position.
     * First and last waypoint can't be silent.
     */
    fun waypointsNames(): List<String> = waypoints
        // silent waypoints can't have a name
        .filterIndexed { index, _ ->
            !waypoints.isSilentWaypoint(index)
        }
        .map {
            when (it.type) {
                is WaypointType.Named -> it.type.name
                else -> ""
            }
        }

    fun coordinatesList(): List<Point> {
        return waypoints.map { it.point }
    }

    private sealed class WaypointType {
        data class Named(val name: String) : WaypointType()
        object Regular : WaypointType()
        object Silent : WaypointType()
    }

    private data class Waypoint(val point: Point, val type: WaypointType)

    private fun List<Waypoint>.isSilentWaypoint(index: Int) =
        this[index].type == WaypointType.Silent && canWaypointBeSilent(index)

    // the first and the last waypoint can't be silent
    private fun List<Waypoint>.canWaypointBeSilent(index: Int): Boolean {
        val isLastWaypoint = index == this.size - 1
        val isFirstWaypoint = index == 0
        return !isLastWaypoint && !isFirstWaypoint
    }
}


