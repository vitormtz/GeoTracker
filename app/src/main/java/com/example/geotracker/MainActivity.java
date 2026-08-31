package com.example.geotracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback {

    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_CODE = 1;

    private TextInputEditText etRouteName;
    private Button btnCapture;
    private AutoCompleteTextView actvSavedRoutes;

    private LocationManager locationManager;
    private boolean isCapturing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable captureRunnable;

    private GoogleMap mMap;
    private final List<LatLng> currentRoutePoints = new ArrayList<>();
    private LatLng currentLocation;

    private DatabaseHelper dbHelper;
    private DatabaseHelper.RouteInfo selectedRoute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        etRouteName = findViewById(R.id.etRouteName);
        btnCapture = findViewById(R.id.btnCapture);
        actvSavedRoutes = findViewById(R.id.actvSavedRoutes);
        Button btnViewRoute = findViewById(R.id.btnViewRoute);
        btnCapture.setOnClickListener(v -> toggleCapture());
        btnViewRoute.setOnClickListener(v -> displaySelectedRoute());

        loadSavedRoutes();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        } else {
            startLocationUpdates();
        }
    }

    private void loadSavedRoutes() {
        List<DatabaseHelper.RouteInfo> routes = dbHelper.getAllRoutes();
        ArrayAdapter<DatabaseHelper.RouteInfo> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, routes);

        actvSavedRoutes.setAdapter(adapter);
        actvSavedRoutes.setOnItemClickListener((parent, view, position, id) -> {
            selectedRoute = (DatabaseHelper.RouteInfo) parent.getItemAtPosition(position);
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setBuildingsEnabled(false);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
        if (currentLocation != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
        }
    }

    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000,
                    10,
                    this);

            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation != null) {
                updateLocationUI(lastLocation);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Erro ao acessar localização: " + e.getMessage());
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        updateLocationUI(location);

        if (isCapturing) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            LatLng point = new LatLng(latitude, longitude);
            currentRoutePoints.add(point);
            updateRouteOnMap();
        }
    }

    private void updateLocationUI(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        currentLocation = new LatLng(latitude, longitude);

        if (!isCapturing && mMap != null && selectedRoute == null) {
            mMap.clear();
            mMap.addMarker(new MarkerOptions()
                    .position(currentLocation)
                    .title("Posição Atual"));
            mMap.animateCamera(CameraUpdateFactory.newLatLng(currentLocation));
        }
    }

    private void toggleCapture() {
        if (!isCapturing) {
            startCapturing();
        } else {
            stopCapturingAndSave();
        }
    }

    private void startCapturing() {
        String routeName = Objects.requireNonNull(etRouteName.getText()).toString().trim();

        if (TextUtils.isEmpty(routeName)) {
            Toast.makeText(this, "Por favor, insira um nome para a rota",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        currentRoutePoints.clear();
        if (mMap != null) mMap.clear();
        isCapturing = true;
        btnCapture.setText("Parar Captura");
        etRouteName.setEnabled(false);
        selectedRoute = null;

        if (currentLocation != null) {
            currentRoutePoints.add(currentLocation);
        }

        captureRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCapturing) {
                    captureCurrentLocation();
                    handler.postDelayed(this, 5000);
                }
            }
        };

        handler.post(captureRunnable);
    }

    private void captureCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                LatLng point = new LatLng(latitude, longitude);

                if (currentRoutePoints.isEmpty() ||
                        !point.equals(currentRoutePoints.get(currentRoutePoints.size() - 1))) {
                    currentRoutePoints.add(point);
                    updateRouteOnMap();
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Erro ao acessar localização: " + e.getMessage());
        }
    }

    private void updateRouteOnMap() {
        if (mMap != null && !currentRoutePoints.isEmpty()) {
            mMap.clear();
            mMap.addMarker(new MarkerOptions()
                    .position(currentRoutePoints.get(0))
                    .title("Início"));

            LatLng lastPoint = currentRoutePoints.get(currentRoutePoints.size() - 1);
            mMap.addMarker(new MarkerOptions()
                    .position(lastPoint)
                    .title("Posição Atual"));

            mMap.addPolyline(new PolylineOptions().addAll(currentRoutePoints));
            mMap.animateCamera(CameraUpdateFactory.newLatLng(lastPoint));
        }
    }

    private void stopCapturingAndSave() {
        isCapturing = false;
        btnCapture.setText("Iniciar Captura de Rota");
        etRouteName.setEnabled(true);
        handler.removeCallbacks(captureRunnable);

        if (!currentRoutePoints.isEmpty() && etRouteName.getText() != null) {
            String routeName = etRouteName.getText().toString().trim();
            if (!TextUtils.isEmpty(routeName)) {
                long routeId = dbHelper.saveRoute(routeName, currentRoutePoints);
                if (routeId > 0) {
                    loadSavedRoutes();
                    Toast.makeText(this, "Rota \"" + routeName + "\" salva com sucesso",
                            Toast.LENGTH_SHORT).show();
                    etRouteName.setText("");
                }
            }
        }
    }

    private void displaySelectedRoute() {
        if (selectedRoute != null) {
            List<LatLng> routePoints = dbHelper.getRoutePoints(selectedRoute.getId());

            if (routePoints != null && !routePoints.isEmpty()) {
                mMap.clear();
                mMap.addMarker(new MarkerOptions()
                        .position(routePoints.get(0))
                        .title("Início"));
                mMap.addMarker(new MarkerOptions()
                        .position(routePoints.get(routePoints.size() - 1))
                        .title("Fim"));
                mMap.addPolyline(new PolylineOptions().addAll(routePoints));
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (LatLng point : routePoints) {
                    builder.include(point);
                }
                int padding = 100;
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                        builder.build(), padding));
            }
        } else {
            Toast.makeText(this, "Selecione uma rota primeiro", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                if (mMap != null && ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedRoutes();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }
}