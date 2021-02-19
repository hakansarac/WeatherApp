package com.hakansarac.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants{


    //TODO: visit https://openweathermap.org to get your api key
    //TODO: const val APP_ID: String = "your own OpenWeatherMap api key"
    const val APP_ID: String = ""

    const val BASE_URL : String = "http://api.openweathermap.org/data/"
    const val METRIC_UNIT : String = "metric"

    // Add the SharedPreferences name and key name for storing the response data in it.
    const val PREFERENCE_NAME = "WeatherAppPreferences"
    const val WEATHER_RESPONSE_DATA = "weather_response_data"

    /**
     * This function is used check the weather the device is connected to the Internet or not.
     */
    fun isNetworkAvailable(context: Context) : Boolean{
        // It answers the queries about the state of network connectivity.
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //M is version 23
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                //for other device how are able to connect with Ethernet
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else ->false
            }
        }else{
            // Returns details about the currently active default data network.
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }
    }
}