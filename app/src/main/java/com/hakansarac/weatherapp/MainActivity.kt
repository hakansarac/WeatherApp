package com.hakansarac.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import com.google.gson.Gson
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

// OpenWeather Link : https://openweathermap.org/api
/**
 * The useful link or some more explanation for this app you can checkout this link :
 * https://medium.com/@sasude9/basic-android-weather-app-6a7c0855caf4
 */

class MainActivity : AppCompatActivity() {

    // A fused location client variable which is further user to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    // A global variable for Progress Dialog
    private var mProgressDialog: Dialog? = null
    private lateinit var mSharedPreferences : SharedPreferences     //to keep last variables to show at opening when cannot get up to date data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)   //if it is private then the values can only be obtained by this application
        //  Call the UI method to populate the data in
        //  the UI which are already stored in sharedPreferences earlier.
        //  At first run it will be blank.
        setupUI()   //setup UI with data remaining from previous use

        /**
         * if user's location information is disabled, take user to location source settings
         * else with third party code(dexter) control the permissions
         * if permissions granted then request location data
         * if any permission is denied then ask to give permission with a Toast message
         */
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()
            // This will redirect you to settings from where you need to turn on the location provider.
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


    /**
     * A function which is used to verify that the location or GPS is enable or not of the user's device.
     * check the location accessibility
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * A function used to show the alert dialog when the permissions are denied and need to allow it from settings app info.
     * If user want to do something but he is has turned off permissions for this feature,
     * then show an alert dialog to give this information and take user to application settings
     */
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

    /**
     * A function to request the current location. Using the fused location provider client.
     */
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
        )
    }

    /**
     * A location callback object of fused location provider client where we will get the current location details.
     */
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

    /**
     * Function is used to get the weather details of the current location based on the latitude longitude
     * third party retrofit: API interfaces are turned into callable objects
     * third party gson: Gson is a Java library that can be used to convert Java Objects into their JSON representation.
     *                   It can also be used to convert a JSON string to an equivalent Java object.
     */
    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if(Constants.isNetworkAvailable(this)){
            /**
             * Add the built-in converter factory first. This prevents overriding its
             * behavior but also ensures correct behavior when using converters that consume all types.
             */
            val retrofit : Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()
            /**
             * Here we map the service interface in which we declares the end point and the API type
             *i.e GET, POST and so on along with the request parameter which are required.
             */
            val service : WeatherService = retrofit.create(WeatherService::class.java)

            /**
             * An invocation of a Retrofit method that sends a request to a web-server and returns a response.
             * Here we pass the required param in the service
             */
            //TODO: APP_ID must be your own OpenWeatherMap api key
            val listCall : Call<WeatherResponse> = service.getWeather(latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID)

            showCustomProgressDialog()  //
            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    // Check weather the response is success or not.
                    if(response.isSuccessful){
                        /** The de-serialized response body of a successful response. */
                        val weatherList: WeatherResponse? = response.body()
                        hideProgressDialog()

                        // Here we have converted the model class into Json String to store it in the SharedPreferences.
                        val weatherResponseJsonString = Gson().toJson(weatherList) //return all data list as String
                        // Save the converted string to shared preferences
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                        Log.i("Response Result:","$weatherList")

                    }else{
                        // If the response is not success then we check the response code.
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

    /**
     * Method is used to show the Custom Progress Dialog.
     */
    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    /**
     * This function is used to dismiss the progress dialog if it is visible to user.
     */
    private fun hideProgressDialog(){
        if(mProgressDialog!=null)
            mProgressDialog!!.dismiss()
    }

    /**
     * Function is used to set the result in the UI elements.
     */
    private fun setupUI(){
        //  Here we get the stored response from
        //  SharedPreferences and again convert back to data object
        //  to populate the data in the UI.

        // Here we have got the latest stored response from the SharedPreference and converted back to the data model object.
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            // For loop to get the required data. And all are populated in the UI.
            for(i in weatherList.weather.indices){
                Log.i("Weather Name",weatherList.weather.toString())
                //visit https://openweathermap.org/weather-conditions to see list of weather conditions codes
                textViewMain.text = weatherList.weather[i].main
                textViewMainDescription.text = weatherList.weather[i].description

                //get the unit type according to country
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

    /**
     * getting type of unit according to country
     */
    private fun getUnit(localValue : String): String? {
        var value = "°C"
        if("US" == localValue || "LR" == localValue || "MM" == localValue){
            value = "°F"
        }
        return value
    }

    /**
     * change unix epoch time to human-readable date
     */
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

    /**
     * setting the options items
     */
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