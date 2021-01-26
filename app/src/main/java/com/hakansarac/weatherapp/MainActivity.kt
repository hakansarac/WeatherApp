package com.hakansarac.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.*
import com.hakansarac.weatherapp.models.WeatherResponse
import com.hakansarac.weatherapp.network.WeatherService
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else {
            Dexter.withActivity(this).withPermissions(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    ).withListener(object : MultiplePermissionsListener {
                        override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                            if (report!!.areAllPermissionsGranted()) {
                                requestLocationData()
                            }

                            if (report.isAnyPermissionPermanentlyDenied) {
                                Toast.makeText(
                                        this@MainActivity,
                                        "You have denied location permission. Please allow it is mandatory.",
                                        Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onPermissionRationaleShouldBeShown(
                                permissions: MutableList<PermissionRequest>?,
                                token: PermissionToken?
                        ) {
                            showRationalDialogForPermissions()
                        }
                    }).onSameThread()
                    .check()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
                .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
                .setPositiveButton(
                        "GO TO SETTINGS"
                ) { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("Cancel") { dialog,_ ->
                    dialog.dismiss()
                }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()
            val service : WeatherService = retrofit.create(WeatherService::class.java)

            //TODO: APP_ID must be your own OpenWeatherMap api key
            val listCall : Call<WeatherResponse> = service.getWeather(latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID)

            showCustomProgressDialog()
            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    if(response.isSuccessful){
                        val weatherList: WeatherResponse? = response.body()
                        hideProgressDialog()
                        if(weatherList != null)
                            setupUI(weatherList)
                        Log.i("Response Result:","$weatherList")

                    }else{
                        val responseCode = response.code()
                        when(responseCode){
                            400 -> Log.e("Error 400","Bad Connection")
                            404 -> Log.e("Error 404","Not Found")
                            else -> Log.e("Error","Generic Error")
                        }
                        hideProgressDialog()
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrr",t.message.toString())
                    hideProgressDialog()
                }

            })

        }else{
            Toast.makeText(this,"Please check your internet connection.",Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if(mProgressDialog!=null)
            mProgressDialog!!.dismiss()
    }

    private fun setupUI(weatherList: WeatherResponse){
        for(i in weatherList.weather.indices){
            Log.i("Weather Name",weatherList.weather.toString())
            //visit https://openweathermap.org/weather-conditions to see list of weather conditions codes
            textViewMain.text = weatherList.weather[i].main
            textViewMainDescription.text = weatherList.weather[i].description

            val value = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                getUnit(application.resources.configuration.locales[0].country.toString())
            }else{
                getUnit(application.resources.configuration.locale.country.toString())
            }
            textViewTemp.text = weatherList.main.temp.toString() + value

            textViewSunriseTime.text = unixTime(weatherList.sys.sunrise)
            textViewSunsetTime.text = unixTime(weatherList.sys.sunset)

            textViewHumidity.text = weatherList.main.humidity.toString() + " per cent"
            textViewMin.text = weatherList.main.temp_min.toString() + " min"
            textViewMax.text = weatherList.main.temp_max.toString() + " max"
            textViewSpeed.text = weatherList.wind.speed.toString()
            textViewName.text = weatherList.name
            textViewCountry.text = weatherList.sys.country

            setIcon(weatherList,i)
        }
    }

    private fun setIcon(weatherList: WeatherResponse,i:Int) {
        when (weatherList.weather[i].icon) {
            "01d" -> imageViewMain.setImageResource(R.drawable.sunny)
            "02d" -> imageViewMain.setImageResource(R.drawable.cloud)
            "03d" -> imageViewMain.setImageResource(R.drawable.cloud)
            "04d" -> imageViewMain.setImageResource(R.drawable.cloud)
            "04n" -> imageViewMain.setImageResource(R.drawable.cloud)
            "10d" -> imageViewMain.setImageResource(R.drawable.rain)
            "11d" -> imageViewMain.setImageResource(R.drawable.storm)
            "13d" -> imageViewMain.setImageResource(R.drawable.snowflake)
            "01n" -> imageViewMain.setImageResource(R.drawable.cloud)
            "02n" -> imageViewMain.setImageResource(R.drawable.cloud)
            "03n" -> imageViewMain.setImageResource(R.drawable.cloud)
            "10n" -> imageViewMain.setImageResource(R.drawable.cloud)
            "11n" -> imageViewMain.setImageResource(R.drawable.rain)
            "13n" -> imageViewMain.setImageResource(R.drawable.snowflake)
        }

    }

    private fun getUnit(localValue : String): String? {
        var value = "°C"
        if("US" == localValue || "LR" == localValue || "MM" == localValue){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex : Long): String?{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                requestLocationData()
                true
            }
            else->super.onOptionsItemSelected(item)
        }
    }
}