@file:Suppress("DEPRECATION")

package com.example.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.AsyncTask
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request


class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mGoogleMap: GoogleMap
    private lateinit var setupRabbitMQ: SetupRabbitMQ
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private var currentPolyline: Polyline? = null
    private var LocationX: LatLng? = null



    data class Marker(var location: LatLng, val initials: String, val icon: BitmapDescriptor)

    val markerMessage = MutableLiveData<List<Marker>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupRabbitMQ = SetupRabbitMQ()
        setupRabbitMQ.defaultExchangeAndQueue()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            initializeMap()
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    setupRabbitMQ.defaultExchangeAndQueue()

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun initializeMap() {
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeMap()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mGoogleMap.isMyLocationEnabled = true
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(52.2297, 21.0122), 10f))
            mGoogleMap.setOnMyLocationChangeListener {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        startListening()
                        val myLocation = LatLng(location.latitude, location.longitude)
                        val markerIcon = bitmapFromTextWithBackground2("MM")
                        mGoogleMap.addMarker(
                            MarkerOptions().position(myLocation).icon(markerIcon)
                        )
                        startSendingLocation(location.latitude, location.longitude)

                    }
                }
            }
            mGoogleMap.setOnMapClickListener { clickedLocation ->
                LocationX = clickedLocation
                val myLocation = LatLng(mGoogleMap.myLocation.latitude, mGoogleMap.myLocation.longitude)
                LocationX?.let { destination ->
                    val directionUrl = getDirectionURL(myLocation, destination, getString(R.string.google_maps_api_key))
                    GetDirection(directionUrl).execute()
                }
            }
        }
    }


    private fun displayMarkers() {
        val markers = markerMessage.value ?: return
        mGoogleMap.clear()
        for (marker in markers) {
            mGoogleMap.addMarker(
                MarkerOptions()
                    .position(marker.location)
                    .title(marker.initials)
                    .icon(marker.icon)
            )
        }
    }

    private fun startListening() {
        val connection = setupRabbitMQ.gimmeFactory().newConnection()
        val channel = connection.createChannel()

        val cancelCallback =
            CancelCallback { consumerTag: String? -> println("Cancelled... $consumerTag") }

        val deliverCallback = DeliverCallback { _: String?, message: Delivery? ->
            val messageBody = String(message!!.body)
            val parts = messageBody.split(" ")

            if (parts.size >= 3) {
                val location = LatLng(parts[0].toDouble(), parts[1].toDouble())
                val initials = parts[2]

                if (initials != "MM") {
                    val currentList = markerMessage.value ?: emptyList()
                    val updatedList = mutableListOf<Marker>()

                    val existing = currentList.find { it.initials == initials }
                    if (existing == null) {
                        updatedList.addAll(currentList)
                        updatedList.add(
                            Marker(
                                location,
                                initials,
                                bitmapFromTextWithBackground(initials)
                            )
                        )
                    } else {
                        existing.location = location
                        updatedList.addAll(currentList)
                    }

                    markerMessage.postValue(updatedList)
                }
            }
        }

        val queueName = "navigation-MM"
        channel.basicConsume(queueName, false, deliverCallback, cancelCallback)
        displayMarkers()
    }


    private fun bitmapFromTextWithBackground(text: String): BitmapDescriptor {
        val backgroundSize = 100
        val textSize = 50
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = textSize.toFloat()
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        val baseline = -paint.ascent()

        val textWidth = (paint.measureText(text) + 0.5f).toInt()
        val width = max(backgroundSize, textWidth)
        val height = backgroundSize + baseline.toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint()
        backgroundPaint.color = randomColor()
        canvas.drawCircle(
            (width / 2).toFloat(),
            (height / 2).toFloat(),
            (backgroundSize / 2).toFloat(),
            backgroundPaint
        )

        val x = width / 2f
        val y = (height / 2f) - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, x, y, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun bitmapFromTextWithBackground2(text: String): BitmapDescriptor {
        val backgroundSize = 100
        val textSize = 50
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = textSize.toFloat()
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        val baseline = -paint.ascent()

        val textWidth = (paint.measureText(text) + 0.5f).toInt()
        val width = max(backgroundSize, textWidth)
        val height = backgroundSize + baseline.toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint()
        backgroundPaint.color = Color.BLACK
        canvas.drawCircle(
            (width / 2).toFloat(),
            (height / 2).toFloat(),
            (backgroundSize / 2).toFloat(),
            backgroundPaint
        )

        val x = width / 2f
        val y = (height / 2f) - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, x, y, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }


    private fun startSendingLocation(latitude: Double, longitude: Double) {
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                sendLocation(latitude, longitude)
                delay(5000)
            }
        }
    }

    private fun sendLocation(latitude: Double, longitude: Double) {
        val initials = "MM"
        val locationMessage = "$latitude $longitude $initials"
        try {
            val factory = SetupRabbitMQ().gimmeFactory()
            val connection = factory.newConnection()
            val channel = connection.createChannel()

            val exchangeName = "navigation"
            val routingKey = ""
            channel.basicPublish(exchangeName, routingKey, null, locationMessage.toByteArray())

            channel.close()
            connection.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun randomColor(): Int {
        val red = Random.nextInt(256)
        val green = Random.nextInt(256)
        val blue = Random.nextInt(256)
        return Color.rgb(red, green, blue)
    }

    fun getDirectionURL(origin: LatLng, dest: LatLng, apiKey: String): String {
        return "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}&destination=${dest.latitude},${dest.longitude}&sensor=false&mode=driving&key=$apiKey"

    }


    @SuppressLint("StaticFieldLeak")
    private inner class GetDirection(val url: String) :
        AsyncTask<Void, Void, List<List<LatLng>>>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void?): List<List<LatLng>> {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val data = response.body!!.string()
            Log.d("GoogleMap", " data : $data")
            val result = ArrayList<List<LatLng>>()
            try {
                val respObj = Gson().fromJson(data, GoogleMapDTO::class.java)

                val path = ArrayList<LatLng>()

                for (i in 0..<respObj.routes[0].legs[0].steps.size) {
                    path.addAll(decodePolyline(respObj.routes[0].legs[0].steps[i].polyline.points))
                }
                result.add(path)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return result
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: List<List<LatLng>>) {
            currentPolyline?.remove()

            val lineoption = PolylineOptions()
            for (i in result.indices) {
                lineoption.addAll(result[i])
                lineoption.width(10f)
                lineoption.color(Color.BLUE)
                lineoption.geodesic(true)
            }
            currentPolyline = mGoogleMap?.addPolyline(lineoption)
            Log.d("GoogleMap", "Polyline added to the map")
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {

        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val latLng = LatLng((lat.toDouble() / 1E5), (lng.toDouble() / 1E5))
            poly.add(latLng)
        }

        return poly
    }
}



