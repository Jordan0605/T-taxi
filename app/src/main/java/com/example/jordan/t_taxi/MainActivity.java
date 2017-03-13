package com.example.jordan.t_taxi;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.google.maps.model.LatLng;
import com.google.maps.GeoApiContext;
import com.google.maps.RoadsApi;
import com.google.maps.model.SnappedPoint;

import org.json.JSONException;
import org.json.JSONObject;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;


public class MainActivity extends AppCompatActivity {

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;
    Timer timer = new Timer();

    private LocationManager mgr;
    private LocationManager mgr_net;
    private FileOutputStream file_gps;
    private FileOutputStream file_gps_net;
    private String path;
    private Button btn_start;
    private boolean flag;
    final Handler handler = new Handler();
    private static final int PAGINATION_OVERLAP = 5;
    private static final int PAGE_SIZE_LIMIT = 100;
    private GeoApiContext mContext;
    DecimalFormat df = new DecimalFormat("#.#");

    private TextView org_dst;
    private int org_counter = 0;
    private double org_oldLat;
    private double org_oldLng;
    private float org_distance = 0;

    private TextView net_dst;
    private int net_counter = 0;
    private double net_oldLat;
    private double net_oldLng;
    private float net_distance = 0;

    private TextView snap_dst;
    List<LatLng> mCapturedLocations;
    private float snappedDistance = 0;

    private TextView net_snap_dst;
    List<LatLng> mCapturedLocations_net;
    private float snappedDistance_net = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences(getString(R.string.PREFS_NAME),0);
        editor=sharedPreferences.edit();
        mContext = new GeoApiContext().setApiKey(getString(R.string.apiKey));
        btn_start = (Button)findViewById(R.id.button);
        org_dst = (TextView)findViewById(R.id.origin_dst);
        net_dst = (TextView)findViewById(R.id.net_dst);
        snap_dst = (TextView)findViewById(R.id.snap);
        net_snap_dst = (TextView)findViewById(R.id.snap_net);
        mCapturedLocations = new ArrayList<>();
        mCapturedLocations_net = new ArrayList<>();

        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);

        final String tmDevice, tmSerial, androidId;
        tmDevice = "" + tm.getDeviceId();
        tmSerial = "" + tm.getSimSerialNumber();
        androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
        String deviceId = deviceUuid.toString();

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"); Calendar c = Calendar.getInstance();
        String date = df.format(c.getTime());

        path = Environment.getExternalStorageDirectory().getPath() + "/Sensorlogger/";
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            file_gps = new FileOutputStream(new File(path, (date + "_gps")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            file_gps_net = new FileOutputStream(new File(path, (date + "_net")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            file_gps.write(deviceId.getBytes());
            file_gps.write('\n');
            file_gps_net.write(deviceId.getBytes());
            file_gps_net.write('\n');

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mgr = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mgr_net = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Criteria criteria = new Criteria();

        mgr.getBestProvider(criteria, true);
        mgr_net.getBestProvider(criteria, true);
        criteria.setAccuracy(Criteria.ACCURACY_FINE);

        flag = false;
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!flag) {
                    btn_start.setText("Stop");
                    mgr.requestLocationUpdates(GPS_PROVIDER, 1000, 0, locationlistener); // 讓locationlistener處理資料有變化時的事情
                    mgr_net.requestLocationUpdates(NETWORK_PROVIDER, 1000, 0, locationlistener_net);

                    timer.schedule(doTask, 30000, 30000);
                    flag = true;
                }
                else{
                    btn_start.setText("Start");
                    mgr.removeUpdates(locationlistener);
                    mgr_net.removeUpdates(locationlistener_net);
                    flag = false;
                    timer.cancel();
                }
            }
        });

        //mgr.addGpsStatusListener(GPSstatusListener);//to get GPS status

        API.get_token( new ResponseListener() {
            public void onResponse(JSONObject response) {
                try {
                    String token = response.getJSONObject("data").getString("token");
                    editor.putString("token", token);
                    editor.commit();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            public void onErrorResponse(VolleyError error) {
                Log.d("Tag", "response error");
                Log.d("Tag", error.toString());
            }
        });

    }


    private final LocationListener locationlistener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            float speed = location.getSpeed() * (float) (3.6);
            long time = location.getTime();
            double height = location.getAltitude();
            float bearing = location.getBearing();
            float[] result = new float[1];
            if(org_counter != 0){
                Location.distanceBetween(org_oldLat, org_oldLng, lat, lng, result);
                org_distance += result[0];
                String s = df.format(org_distance / 1000);
                org_dst.setText(s + " km");
            }
            org_oldLat = lat;
            org_oldLng = lng;

            mCapturedLocations.add(new LatLng(lat, lng));

            org_counter++;

            String data = time + "," + lat + "," + lng + "," + speed + "," + height + "," + bearing + "\n";
            if(data != null){
                try {
                    file_gps.write(data.getBytes());
                    //Toast.makeText(MainActivity.this, "raw GPS written.", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }


        @Override
        public void onProviderDisabled(String provider) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onProviderEnabled(String provider) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // TODO Auto-generated method stub
        }

    };

    private final LocationListener locationlistener_net = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            float speed = location.getSpeed() * (float) (3.6);
            long time = location.getTime();
            double height = location.getAltitude();
            float bearing = location.getBearing();
            float[] result = new float[1];
            if(net_counter != 0){
                Location.distanceBetween(net_oldLat, net_oldLng, lat, lng, result);
                net_distance += result[0];
                String s = df.format(net_distance / 1000);
                net_dst.setText(s + " km");
            }
            net_oldLat = lat;
            net_oldLng = lng;
            net_counter++;

            mCapturedLocations_net.add(new LatLng(lat, lng));

            String data = time + "," + lat + "," + lng + "," + speed + "," + height + "," + bearing + "\n";
            if(data != null){
                try {
                    file_gps_net.write(data.getBytes());
                    //Toast.makeText(MainActivity.this, "net GPS written.", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        @Override
        public void onProviderDisabled(String provider) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onProviderEnabled(String provider) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // TODO Auto-generated method stub
        }

    };

    TimerTask doTask = new TimerTask() {
        @Override
        public void run() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        //Toast.makeText(MainActivity.this, "in doTask.", Toast.LENGTH_SHORT).show();
                        float[] result = new float[1];
                        if(!mCapturedLocations.isEmpty()){
                            //Toast.makeText(MainActivity.this, "mCapture isn't empty.", Toast.LENGTH_SHORT).show();
                            List<SnappedPoint> snappedPoints = snapToRoads(mContext, mCapturedLocations);

                            for(int i=0 ; i<snappedPoints.size()-1 ; i++){
                                    result[0] = 0;
                                    Location.distanceBetween(snappedPoints.get(i).location.lat, snappedPoints.get(i).location.lng, snappedPoints.get(i+1).location.lat, snappedPoints.get(i+1).location.lng, result);
                                    snappedDistance += result[0];
                                    //Toast.makeText(MainActivity.this, "dst = " + snappedDistance, Toast.LENGTH_SHORT).show();
                            }
                        }
                        String s = df.format(snappedDistance/1000);
                        snap_dst.setText(s + " km");
                        mCapturedLocations.clear();
                        //Toast.makeText(MainActivity.this, "location clear.", Toast.LENGTH_SHORT).show();

                        float[] result_net = new float[1];
                        if(!mCapturedLocations_net.isEmpty()){
                            List<SnappedPoint> snappedPoints_net = snapToRoads(mContext, mCapturedLocations_net);

                            for(int j=0 ; j<snappedPoints_net.size()-1 ; j++){
                                if(j != snappedPoints_net.size()){
                                    result_net[0] = 0;
                                    Location.distanceBetween(snappedPoints_net.get(j).location.lat, snappedPoints_net.get(j).location.lng, snappedPoints_net.get(j+1).location.lat, snappedPoints_net.get(j+1).location.lng, result_net);
                                    snappedDistance_net += result_net[0];
                                }
                            }
                        }
                        String s_net = df.format(snappedDistance_net/1000);
                        net_snap_dst.setText(s_net + " km");
                        mCapturedLocations_net.clear();
                    }
                    catch (Exception e){
                        Toast.makeText(MainActivity.this, "error. ", Toast.LENGTH_SHORT).show();
                        Log.e("ERROR", "ERROR IN CODE: " + e.toString());
                        e.printStackTrace();
                    }
                }
            });
        }
    };

    private List<SnappedPoint> snapToRoads(GeoApiContext context, List<LatLng> CapturedLocations) throws Exception {
        //Toast.makeText(MainActivity.this, "in snapToRoads.", Toast.LENGTH_SHORT).show();
        List<SnappedPoint> snappedPoints = new ArrayList<>();

        int offset = 0;
        while (offset < CapturedLocations.size()) {
            // Calculate which points to include in this request. We can't exceed the APIs
            // maximum and we want to ensure some overlap so the API can infer a good location for
            // the first few points in each request.
            if (offset > 0) {
                offset -= PAGINATION_OVERLAP;   // Rewind to include some previous points
            }
            int lowerBound = offset;
            int upperBound = Math.min(offset + PAGE_SIZE_LIMIT, CapturedLocations.size());

            // Grab the data we need for this page.
            LatLng[] page = CapturedLocations
                    .subList(lowerBound, upperBound)
                    .toArray(new LatLng[upperBound - lowerBound]);

            // Perform the request. Because we have interpolate=true, we will get extra data points
            // between our originally requested path. To ensure we can concatenate these points, we
            // only start adding once we've hit the first new point (i.e. skip the overlap).
            SnappedPoint[] points = RoadsApi.snapToRoads(context , true, page).await();
            boolean passedOverlap = false;
            for (SnappedPoint point : points) {
                if (offset == 0 || point.originalIndex >= PAGINATION_OVERLAP - 1) {
                    passedOverlap = true;
                }
                if (passedOverlap) {
                    snappedPoints.add(point);
                }
            }

            offset = upperBound;
        }
        return snappedPoints;
    }


    private void fileupload(final String filepath,final String filename) throws IOException {

            final String token = sharedPreferences.getString("token", null);
            API.upload_file(filepath, token, filename, new ResponseListener() {
                public void onResponse(JSONObject response) {
                    File file = new File(filepath+"/"+filename);
                    file.delete();
                }

                public void onErrorResponse(VolleyError error) {
                    JSONObject response = null;
                    Log.d("Tag status", error + ">>" +error.networkResponse+"\n");


                    try {
                        if(error.networkResponse != null) {
                            response = new JSONObject(new String(error.networkResponse.data));
                            Log.d("Tag", response.toString());
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    //Log.d("Tag", String.valueOf(error.networkResponse.statusCode));

                }
            });


    }


}
