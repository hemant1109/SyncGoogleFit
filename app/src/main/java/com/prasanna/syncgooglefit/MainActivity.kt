package com.prasanna.syncgooglefit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.material.snackbar.Snackbar
import com.prasanna.syncgooglefit.databinding.ActivityMainBinding
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION: Int = 101
    private lateinit var account: GoogleSignInAccount
    private val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 101
    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_main)
        account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this, // your activity
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, // e.g. 1
                account,
                fitnessOptions
            )
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    recognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    recognitionPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    recognitionPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    recognitionPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
                }
            } else {
                accessGoogleFit()
                // Note: Fitness.SensorsApi.findDataSources() requires the
                // ACCESS_FINE_LOCATION permission.
                Fitness.getSensorsClient(
                    this,
                    GoogleSignIn.getAccountForExtension(this, fitnessOptions)
                ).findDataSources(
                    DataSourcesRequest.Builder()
                        .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                        .setDataSourceTypes(DataSource.TYPE_RAW)
                        .build()
                ).addOnSuccessListener { dataSources ->
                    dataSources.forEach {
                        Log.i("getHistoryClient", "Data source found: ${it.streamIdentifier}")
                        Log.i("getHistoryClient", "Data Source type: ${it.dataType.name}")

                        if (it.dataType == DataType.TYPE_STEP_COUNT_DELTA) {
                            Log.i("getHistoryClient", "Data source for STEP_COUNT_DELTA found!")
                        }
                    }
                }.addOnFailureListener { e ->
                    Log.e("getHistoryClient", "Find data sources request failed", e)
                }
            }
        }
    }


    private val recognitionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onStoragePermissionResult
    )

    private fun onStoragePermissionResult(isPermissionGranted: Boolean) {
        if (isPermissionGranted) {
            accessGoogleFit()
            // Note: Fitness.SensorsApi.findDataSources() requires the
            // ACCESS_FINE_LOCATION permission.
            Fitness.getSensorsClient(
                this,
                GoogleSignIn.getAccountForExtension(this, fitnessOptions)
            ).findDataSources(
                DataSourcesRequest.Builder()
                    .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                    .setDataSourceTypes(DataSource.TYPE_RAW)
                    .build()
            ).addOnSuccessListener { dataSources ->
                dataSources.forEach {
                    Log.i("getHistoryClient", "Data source found: ${it.streamIdentifier}")
                    Log.i("getHistoryClient", "Data Source type: ${it.dataType.name}")

                    if (it.dataType == DataType.TYPE_STEP_COUNT_DELTA) {
                        Log.i("getHistoryClient", "Data source for STEP_COUNT_DELTA found!")
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("getHistoryClient", "Find data sources request failed", e)
            }
        } else {
            val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACTIVITY_RECOGNITION
            )
            if (!shouldShowRationale) {
                // permission is permanently denied
                binding.root.snackBarMessage(
                    stringId = R.string.allow_recognition_permission,
                    duration = Snackbar.LENGTH_LONG,
                    actionStringId = R.string.btn_settings
                ) {
                    //Navigation.findNavController(binding.root).popBackStack()
                    // send to app settings if permission is denied permanently
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts(
                        "package", packageName, null
                    )
                    startActivity(intent)
                }
            }
        }
    }

    private fun accessGoogleFit() {
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endSeconds = end.timeInMillis
        val start = end.apply {
            add(Calendar.MONTH, -1)
        }
        val startSeconds = start.timeInMillis

        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
            .setTimeRange(startSeconds, endSeconds, TimeUnit.MILLISECONDS)
            .bucketByTime(1, TimeUnit.DAYS)
            .build()
        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                // Use response data here
                Log.i("getHistoryClient", "OnSuccess() ${response.dataSets.size}")
                for (dataSet in response.buckets.flatMap { it.dataSets }) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        dumpDataSet(dataSet)
                    }
                }
            }.addOnFailureListener { e -> Log.d("getHistoryClient", "OnFailure()", e) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun dumpDataSet(dataSet: DataSet) {
        Log.i("getHistoryClient", "Data returned for Data type: ${dataSet.dataType.name}")
        for (dp in dataSet.dataPoints) {
            Log.i("getHistoryClient", "Data point:")
            Log.i("getHistoryClient", "\tType: ${dp.dataType.name}")
            Log.i("getHistoryClient", "\tStart: ${dp.getStartTimeString()}")
            Log.i("getHistoryClient", "\tEnd: ${dp.getEndTimeString()}")
            for (field in dp.dataType.fields) {
                Log.i(
                    "getHistoryClient",
                    "\tField: ${field.name.toString()} Value: ${dp.getValue(field)}"
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun DataPoint.getStartTimeString() =
        Instant.ofEpochSecond(this.getStartTime(TimeUnit.SECONDS))
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime().toString()

    @RequiresApi(Build.VERSION_CODES.O)
    fun DataPoint.getEndTimeString() = Instant.ofEpochSecond(this.getEndTime(TimeUnit.SECONDS))
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime().toString()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> when (requestCode) {
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE -> accessGoogleFit()
                else -> {
                    // Result wasn't from Google Fit
                }
            }
            else -> {
                // Permission not granted
            }
        }
    }
}

private fun View.snackBarMessage(
    @StringRes stringId: Int,
    duration: Int,
    @StringRes actionStringId: Int?,
    actionListener: View.OnClickListener,
) {
    Snackbar.make(this, stringId, duration).apply {
        if (actionStringId != null) {
            setAction(actionStringId, actionListener)
        }
    }.show()
}
