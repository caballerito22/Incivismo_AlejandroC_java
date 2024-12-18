package com.example.incivismo_alejandroc_java.ui.home;


import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.incivismo_alejandroc_java.databinding.FragmentHomeBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private ActivityResultLauncher<String[]> locationPermissionRequest;

    private FragmentHomeBinding binding;

    private Location mLastLocation;
    private FusedLocationProviderClient mFusedLocationClient;

    private boolean mTrackingLocation;
    private LocationCallback mLocationCallback;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getContext());

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.localitzacio;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        locationPermissionRequest = registerForActivityResult(new ActivityResultContracts
                        .RequestMultiplePermissions(), result -> {
                    Boolean fineLocationGranted = result.getOrDefault(
                            Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(
                            Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (fineLocationGranted != null && fineLocationGranted) {
                        getLocation();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        getLocation();
                    } else {
                        Toast.makeText(requireContext(), "No concedeixen permisos", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (mTrackingLocation) {
                    fetchAddress(locationResult.getLastLocation());
                }
            }
        };

        binding.buttonLocation.setOnClickListener((View clickedView) -> {
            if (!mTrackingLocation) {
                startTrackingLocation();
            } else {
                stopTrackingLocation();
            }
        });

        return root;

    }

    private void startTrackingLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Request permisssions", Toast.LENGTH_SHORT).show();
            locationPermissionRequest.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        } else {
            Toast.makeText(requireContext(), "getLocation: permissions granted", Toast.LENGTH_SHORT).show();
            mFusedLocationClient.requestLocationUpdates(getLocationRequest(), mLocationCallback, null);
        }
        binding.localitzacio.setText("Carregant...");
        binding.localitzacio.setVisibility(ProgressBar.VISIBLE);
        mTrackingLocation = true;
        binding.buttonLocation.setText("Aturar el seguiment de la ubicació");
    }

    private LocationRequest getLocationRequest() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }

    private void stopTrackingLocation() {
        if (mTrackingLocation) {
            binding.localitzacio.setVisibility(ProgressBar.INVISIBLE);
            mTrackingLocation = false;
            binding.buttonLocation.setText("Comença a seguir la ubicació");
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    private void getLocation() {
        if (ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(requireContext(), "Request permisssions", Toast.LENGTH_SHORT).show();
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(
                    location -> {
                        if (location != null) {
                            mLastLocation = location;
                            binding.localitzacio.setText(
                                    String.format("Latitud: %1$.4f \n Longitud: %2$.4f\n Hora: %3$tr",
                                            mLastLocation.getLatitude(),
                                            mLastLocation.getLongitude(),
                                            mLastLocation.getTime()));
                        } else {
                            binding.localitzacio.setText("Sense localització coneguda");
                        }
                    });
        }

    }

    private void fetchAddress(Location location) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        Geocoder geocoder = new Geocoder(requireContext(),
                Locale.getDefault());

        executor.execute(() -> {

            List<Address> addresses = null;
            String resultMessage = "";

            try {
                addresses = geocoder.getFromLocation(
                        location.getLatitude(),
                        location.getLongitude(),
                        // En aquest cas, sols volem una única adreça:
                        1);


                if (addresses == null || addresses.size() == 0) {
                    if (resultMessage.isEmpty()) {
                        resultMessage = "No s'ha trobat cap adreça";
                        Log.e("INCIVISME", resultMessage);
                    }
                } else {
                    Address address = addresses.get(0);
                    ArrayList<String> addressParts = new ArrayList<>();

                    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                        addressParts.add(address.getAddressLine(i));
                    }

                    resultMessage = TextUtils.join("\n", addressParts);
                    String finalResultMessage = resultMessage;
                    handler.post(() -> {
                        // Aquest codi s'executa en primer pla.
                        binding.localitzacio.setText(String.format(
                                "Direcció: %1$s \n Hora: %2$tr",
                                finalResultMessage, System.currentTimeMillis()));
                    });
                }

            } catch (IOException ioException) {
                resultMessage = "Servei no disponible";
                Log.e("INCIVISME", resultMessage, ioException);
            } catch (IllegalArgumentException illegalArgumentException) {
                resultMessage = "Coordenades no vàlides";
                Log.e("INCIVISME", resultMessage + ". " +
                        "Latitude = " + location.getLatitude() +
                        ", Longitude = " +
                        location.getLongitude(), illegalArgumentException);
            }
        });
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}