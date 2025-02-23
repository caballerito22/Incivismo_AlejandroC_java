package com.example.incivismo_alejandroc_java.ui.notifications;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.incivismo_alejandroc_java.Incidencia;
import com.example.incivismo_alejandroc_java.R;
import com.example.incivismo_alejandroc_java.databinding.FragmentNotificationsBinding;
import com.example.incivismo_alejandroc_java.ui.home.SharedViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

// En el notifications hemos usado el shared y con el método de getCurrentLatLang hemos obtenido donde estamos (en el mapa la flecha)
public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private SharedViewModel sharedViewModel;
    private FirebaseAuth auth;
    private DatabaseReference incidencies;

    private ImageView foto;
    String mCurrentPhotoPath;
    private Uri photoURI;

    static final int REQUEST_TAKE_PHOTO = 1;
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(
                getContext().getPackageManager()) != null) {

            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                System.out.println("no te va rey");
            }

            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(getContext(),
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }
    private File createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Cargar configuración de OpenStreetMap
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // Configuración del mapa
        binding.map.setTileSource(TileSourceFactory.MAPNIK);
        binding.map.setMultiTouchControls(true);
        IMapController mapController = binding.map.getController();
        mapController.setZoom(14.5);

        // Cargar SharedViewModel
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        // Observador de la ubicación actual
        sharedViewModel.getCurrentLatLng().observe(getViewLifecycleOwner(), location -> {
            if (location != null) {
                GeoPoint geoPoint = new GeoPoint(location.latitude, location.longitude);
                mapController.setCenter(geoPoint);
            }
        });

        //cambiamos view por root
        ImageView Foto = root.findViewById(R.id.buttonFoto);
        Button buttonFoto = root.findViewById(R.id.imageLocation);

        buttonFoto.setOnClickListener(button -> {
            dispatchTakePictureIntent();
        });

        // Overlay de la ubicación del usuario
        MyLocationNewOverlay myLocationNewOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), binding.map);
        myLocationNewOverlay.enableMyLocation();
        binding.map.getOverlays().add(myLocationNewOverlay);

        // Overlay de la brújula
        CompassOverlay compassOverlay = new CompassOverlay(requireContext(), new InternalCompassOrientationProvider(requireContext()), binding.map);
        compassOverlay.enableCompass();
        binding.map.getOverlays().add(compassOverlay);

        // Conectar a Firebase
        auth = FirebaseAuth.getInstance();
        DatabaseReference base = FirebaseDatabase.getInstance().getReference();
        DatabaseReference users = base.child("users");
        DatabaseReference uid = users.child(auth.getUid());
        incidencies = uid.child("incidencies");

        // Obtener incidencias de Firebase y mostrar marcadores
        incidencies.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, String s) {
                Incidencia incidencia = dataSnapshot.getValue(Incidencia.class);

                if (incidencia != null) {
                    // Crear un GeoPoint con las coordenadas de la incidencia
                    GeoPoint location = new GeoPoint(
                            Double.parseDouble(incidencia.getLatitud()),
                            Double.parseDouble(incidencia.getLongitud())
                    );

                    // Crear un marcador
                    Marker marker = new Marker(binding.map);
                    marker.setPosition(location);
                    marker.setTitle(incidencia.getProblema());
                    marker.setSnippet(incidencia.getDireccio());

                    // Añadir el marcador al mapa
                    binding.map.getOverlays().add(marker);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, String s) {}
            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {}
            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, String s) {}
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });

        return root;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO) {
            if (resultCode == Activity.RESULT_OK) {
                Glide.with(this).load(photoURI).into(foto);
            } else {
                Toast.makeText(getContext(), (
                        "Picture wasn't taken!"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.map.onResume(); // Reanudar mapa para los overlays
    }

    @Override
    public void onPause() {
        super.onPause();
        binding.map.onPause(); // Pausar mapa para los overlays
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
