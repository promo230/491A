package com.example.owner.ppkrinc;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class ParkingMapsActivity
        extends FragmentActivity
        implements OnMapReadyCallback {

    private GoogleMap mMap;
    public static final String TAG = ParkingMapsActivity.class.getSimpleName();
    private FirebaseFirestore db;
    private boolean mLocationPermissionGranted;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    private DocumentReference docReference;
    private CollectionReference colReference;
    private String matchDocID;
    private ListenerRegistration reg;
    private Timestamp timestampID;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parking_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) this.getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        db = FirebaseFirestore.getInstance();
        timestampID = Timestamp.now();



    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        getLocationPermission();

        FusedLocationProviderClient currentLocation = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        displayCurrentLocation(currentLocation);
    }

    private void displayCurrentLocation(FusedLocationProviderClient currentLocation) {
        try {
            if (mLocationPermissionGranted) {
                Task<Location> lastLocation = currentLocation.getLastLocation();
                lastLocation.addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Set the map's camera position to the current location of the device.
                        Location lastKnownLocation = task.getResult();

                        Log.e(TAG, lastKnownLocation.toString());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(lastKnownLocation.getLatitude(),
                                        lastKnownLocation.getLongitude()), 16));
                        mMap.addMarker(new MarkerOptions()
                                .title("Parking Location")
                                .position( new LatLng(lastKnownLocation.getLatitude(),
                                        lastKnownLocation.getLongitude())).
                                        draggable(true));
                        saveLocation(lastKnownLocation);
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.");
                        Log.e(TAG, "Exception: %s", task.getException());
                        mMap.getUiSettings().setMyLocationButtonEnabled(false);
                    }
                });
            }
        }catch (SecurityException e){
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void saveLocation(Location lastKnownLocation) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        Bundle extraLocationData = getIntent().getExtras();

        Map<String, Object> location = new HashMap<>();

        String activityType = extraLocationData.getString("type");
        if (activityType.equalsIgnoreCase("Post")) {
            location.put("descriptor", extraLocationData.getString("descriptor"));
            location.put("lat", lastKnownLocation.getLatitude());
            location.put("locationShare", extraLocationData.getBoolean("locationShare"));
            location.put("long", lastKnownLocation.getLongitude());
            location.put("matchStatus", 0);
            location.put("parkingLot", extraLocationData.get("parkingLot"));
            location.put("rideShare", extraLocationData.getBoolean("rideShare"));
            location.put("userID", currentUser.getUid());
        } else {
            location.put("lat", lastKnownLocation.getLatitude());
            location.put("long", lastKnownLocation.getLongitude());
            location.put("matchStatus", 0);
            location.put("rideShare", extraLocationData.getBoolean("rideShare"));
            location.put("userID", currentUser.getUid());

        }

        location.put("timestamp", timestampID);


        docReference = db.collection("location" + extraLocationData.getString("type")).document(timestampID.toString());
        docReference.set(location);
        if (docReference.getId() != null) {
            Toast.makeText(ParkingMapsActivity.this,
                    "" + docReference.getId(),
                    Toast.LENGTH_SHORT).show();
        }


        if (activityType.equalsIgnoreCase("Request")) {
            //waitForPosting();
            colReference = db.collection("locationPost");
            //Query postingQuery = colReference.orderBy("timestamp").limit(1);
            //colReference

            ListenerRegistration reg = db.collection("locationPost").addSnapshotListener(new EventListener<QuerySnapshot>() {

                @Override
                public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {

                    if (e != null) {
                        Toast.makeText(ParkingMapsActivity.this,
                                "bummer",
                                Toast.LENGTH_SHORT).show();
                        Log.w("TAG", "listen:error", e);
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        Toast.makeText(ParkingMapsActivity.this,
                                "success " +queryDocumentSnapshots.size(),
                                Toast.LENGTH_SHORT).show();

                        List<DocumentSnapshot> query = queryDocumentSnapshots.getDocuments();

                        for(int index = 0; index < query.size(); ){
                            if(query.get(index).get("matchStatus").toString().equalsIgnoreCase("1")){
                                query.remove(index);
                            }
                            else{
                                index++;
                            }
                        }

                        Toast.makeText(ParkingMapsActivity.this,
                                "" + query.size(),
                                Toast.LENGTH_SHORT).show();
                        Map<String, Object> newLoc;
                        for(DocumentSnapshot doc : query){
                            newLoc = doc.getData();
                            Toast.makeText(ParkingMapsActivity.this,
                                    "" + doc.getId(),
                                    Toast.LENGTH_SHORT).show();
                            newLoc.put("matchStatus",1);
                            colReference.document(doc.getId()).set(newLoc);
                            Intent intent = new Intent(getApplicationContext(), ConfirmationPage.class);
                            Bundle extraMatchData = new Bundle();

                            extraMatchData.putString("type", "Request");
                            extraMatchData.putString("requestID", timestampID.toString());
                            extraMatchData.putString("postID", doc.getId());

                            intent.putExtras(extraMatchData);

                            startActivity(intent);
                            break;
                        }

                        Toast.makeText(ParkingMapsActivity.this,
                                "success ",
                                Toast.LENGTH_SHORT).show();


                    }
                    /*
                    int i = 0;
                    for(DocumentChange doc : queryDocumentSnapshots.getDocumentChanges()){
                        switch (doc.getType()) {
                            case ADDED:
                                Toast.makeText(ParkingMapsActivity.this,
                                        "success " + i,
                                        Toast.LENGTH_SHORT).show();
                                i++;
                                break;
                            case MODIFIED:
                                Toast.makeText(ParkingMapsActivity.this,
                                        "bummer",
                                        Toast.LENGTH_SHORT).show();
                                break;
                            case REMOVED:
                                Toast.makeText(ParkingMapsActivity.this,
                                        "bummer",
                                        Toast.LENGTH_SHORT).show();
                                break;
                        }
                    }
                    */
                }
            });

            //reg.remove();
        }
        else{
            //waitForSearch();

            docReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
                @Override
                public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                    if (e != null) {
                        Toast.makeText(ParkingMapsActivity.this,
                                "bummer",
                                Toast.LENGTH_SHORT).show();
                        Log.w("TAG", "listen:error", e);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {

                        Toast.makeText(ParkingMapsActivity.this,
                                "success " ,
                                Toast.LENGTH_SHORT).show();

                            Intent intent = new Intent(getApplicationContext(), ConfirmationPage.class);
                            Bundle extraMatchData = new Bundle();

                            extraMatchData.putString("type", "Post");
                            extraMatchData.putString("postID", timestampID.toString());

                            intent.putExtras(extraMatchData);

                            startActivity(intent);
                    }
                }
            });

        }
            /*
            colReference.addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                    if(queryDocumentSnapshots != null && queryDocumentSnapshots.size()>0){
                        Toast.makeText(ParkingMapsActivity.this,
                                queryDocumentSnapshots.size(),
                                Toast.LENGTH_SHORT).show();


                    Query postingQuery = queryDocumentSnapshots.getQuery().orderBy("timestamp");
                    postingQuery.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if(task.isSuccessful()){
                                Map<String, Object> newLoc;
                                for(QueryDocumentSnapshot document : task.getResult()){
                                    newLoc = document.getData();
                                    if(newLoc.get("matchStatus").toString().equalsIgnoreCase("1")) {
                                        newLoc.put("matchStatus",1);
                                        colReference.document(document.getId()).set(newLoc);
                                        Toast.makeText(ParkingMapsActivity.this,
                                                "" + document.getData().get("matchStatus") + ((Timestamp)document.getData().get("timestamp")),
                                                Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                }
                            }
                        }
                    });
                    */

    }

    private void waitForPosting(){
        colReference = db.collection("locationPost");
        //Query postingQuery = colReference.orderBy("timestamp").limit(1);

        colReference.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                if(queryDocumentSnapshots != null && queryDocumentSnapshots.size()>0){
                    Toast.makeText(ParkingMapsActivity.this,
                            queryDocumentSnapshots.size(),
                            Toast.LENGTH_SHORT).show();

                    /*
                    Query postingQuery = queryDocumentSnapshots.getQuery().orderBy("timestamp");
                    postingQuery.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if(task.isSuccessful()){
                                Map<String, Object> newLoc;
                                for(QueryDocumentSnapshot document : task.getResult()){
                                    newLoc = document.getData();
                                    if(newLoc.get("matchStatus").toString().equalsIgnoreCase("1")) {
                                        newLoc.put("matchStatus",1);
                                        colReference.document(document.getId()).set(newLoc);
                                        Toast.makeText(ParkingMapsActivity.this,
                                                "" + document.getData().get("matchStatus") + ((Timestamp)document.getData().get("timestamp")),
                                                Toast.LENGTH_SHORT).show();
                                        break;
                                    }
                                }
                            }
                        }
                    });
                    */
                }
            }
        });

        /*
        Query postingQuery = colReference.orderBy("timestamp");

        postingQuery.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()){
                    Map<String, Object> newLoc;
                    for(QueryDocumentSnapshot document : task.getResult()){
                        newLoc = document.getData();
                        if(newLoc.get("matchStatus").toString().equalsIgnoreCase("1")) {
                            newLoc.put("matchStatus",1);
                            colReference.document(document.getId()).set(newLoc);
                            Toast.makeText(ParkingMapsActivity.this,
                                    "" + document.getData().get("matchStatus") + ((Timestamp)document.getData().get("timestamp")),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        }
                    }
                }
            }
        });
        */

    }

    /**
     * Prompts the user for permission to use the device location.
     */
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }
    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }
    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

}
