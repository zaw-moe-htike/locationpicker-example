package com.zawmoehtike.goo_map_b2bpurpose

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private lateinit var mLastKnownLocation: Location
    private lateinit var locationCallback: LocationCallback
    private var DEFAULT_ZOOM = 18F
    private var mapView: View? = null

    private var address = ""
    private var latLong = ""

    private var TAG: String = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setUpUI()
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        // check if all permissions are granted
                        if (report.areAllPermissionsGranted()) {
                            setUpUI()
                        }
                        // check for permanent denial of any permission
                        if (report.isAnyPermissionPermanentlyDenied) {
                            // show alert dialog navigating to Settings
                            //showSettingsDialog()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: List<PermissionRequest>,
                        token: PermissionToken
                    ) {
                        token.continuePermissionRequest()
                    }
                })
                .withErrorListener {
                    Toast.makeText(
                        this,
                        "Error occurred! ",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onSameThread()
                .check()
        }
    }

    private fun setUpUI() {
        layoutBase.visibility = View.VISIBLE

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        mapView = mapFragment.view

        mFusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)
        Places.initialize(this, getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)
        val token = AutocompleteSessionToken.newInstance()

        etAddress.setOnClickListener {
            // Set the fields to specify which types of place data to return.
            val fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)

            val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields).build(this)

            startActivityForResult(intent, Constants.AUTO_COMPLETE_ADDRESS_REQ_CODE)
        }

        btnPick.setOnClickListener {
            Toast.makeText(this, "Address: " + address + "\nLatLong: " + latLong, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if(requestCode == Constants.AUTO_COMPLETE_ADDRESS_REQ_CODE && resultCode == Activity.RESULT_OK) {
            val place = Autocomplete.getPlaceFromIntent(intent!!)

            etAddress.setText(place.address)
            Log.d(TAG, "Selected location: " + place.address)

            var selectedLatLong: LatLng = place.latLng!!
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLong, DEFAULT_ZOOM));
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.isMyLocationEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true
        mMap.uiSettings.isScrollGesturesEnabledDuringRotateOrZoom = false

        if (mapView != null && mapView!!.findViewById<View?>("1".toInt()) != null) {
            val locationButton =
                (mapView!!.findViewById<View>("1".toInt())
                    .parent as View).findViewById<View>("2".toInt())
            val layoutParams =
                locationButton.layoutParams as RelativeLayout.LayoutParams
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
            layoutParams.setMargins(0, 0, 40, 180)
        }

        //check if gps is enabled or not and then request user to enable it
        //check if gps is enabled or not and then request user to enable it
        val locationRequest = LocationRequest.create()
        locationRequest.interval = 10000
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder =
            LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val settingsClient = LocationServices.getSettingsClient(this)
        val task =
            settingsClient.checkLocationSettings(builder.build())

        task.addOnSuccessListener(
            this,
            OnSuccessListener<LocationSettingsResponse?> { getDeviceLocation() })

        task.addOnFailureListener(this,
            OnFailureListener { e ->
                if (e is ResolvableApiException) {
                    val resolvable = e as ResolvableApiException
                    try {
                        resolvable.startResolutionForResult(this, 51)
                    } catch (e1: IntentSender.SendIntentException) {
                        e1.printStackTrace()
                    }
                }
            })

        mMap.setOnCameraMoveListener {
            ivMarker.setImageResource(R.drawable.ic_place_marker_on)
        }

        var center = mMap.cameraPosition.target

        mMap.setOnCameraIdleListener {
            center = mMap.cameraPosition.target

            Log.d(TAG,center.latitude.toString() + ", " + center.longitude.toString())

            latLong = center.latitude.toString() + ", " + center.longitude
            ivMarker.setImageResource(R.drawable.ic_place_marker_off)

            val geoCoder = Geocoder(this, Locale.getDefault())

            try {
                val addresses = geoCoder.getFromLocation(center.latitude, center.longitude, 1)

                if (addresses != null) {
                    val returnedAddress = addresses[0]
                    val firstReturnedAddress = StringBuilder("")

                    for (i in 0..returnedAddress.maxAddressLineIndex) {
                        firstReturnedAddress.append(returnedAddress.getAddressLine(i)).append("\n")
                    }

                    address = firstReturnedAddress.toString()

                    etAddress.setText(address)
                    Log.d(TAG, firstReturnedAddress.toString())
                } else {
                    etAddress.setText("No Address returned!")
                    Log.d(TAG, "No Address returned!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                etAddress.setText("Cannot get Address!")
                Log.d(TAG, "Cannot get Address!")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        mFusedLocationProviderClient.lastLocation
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    mLastKnownLocation = task.result!!

                    if (mLastKnownLocation != null) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(mLastKnownLocation.latitude, mLastKnownLocation.longitude), DEFAULT_ZOOM))
                    } else {
                        val locationRequest = LocationRequest.create()
                        locationRequest.interval = 10000
                        locationRequest.fastestInterval = 5000
                        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

                        locationCallback = object : LocationCallback() {
                            override fun onLocationResult(locationResult: LocationResult) {
                                super.onLocationResult(locationResult)

                                if (locationResult == null) {
                                    return
                                }

                                mLastKnownLocation = locationResult.lastLocation
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(mLastKnownLocation.latitude, mLastKnownLocation.longitude), DEFAULT_ZOOM))
                                mFusedLocationProviderClient.removeLocationUpdates(locationCallback)
                            }
                        }

                        mFusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null)
                    }
                } else {
                    Toast.makeText(this, "Unable to get last location", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
