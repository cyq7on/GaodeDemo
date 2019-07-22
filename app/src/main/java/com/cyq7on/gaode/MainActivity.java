package com.cyq7on.gaode;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;

import org.jokar.permissiondispatcher.annotation.NeedsPermission;
import org.jokar.permissiondispatcher.annotation.OnNeverAskAgain;
import org.jokar.permissiondispatcher.annotation.OnPermissionDenied;
import org.jokar.permissiondispatcher.annotation.RuntimePermissions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    @BindView(R.id.mapView)
    MapView mapView;
    @BindView(R.id.cb_path)
    CheckBox cbPath;
    private Unbinder unbinder;
    private MapView mMapView;
    private AMap aMap;
    @BindView(R.id.iv_compass)
    ImageView ivCompass;
    private float lastBearing = 0;
    private Marker locationMarker;
    private Map<String,Marker> planeMarkers = new HashMap<>();
    private Map<String, Polyline> flyPaths = new HashMap<>();
    private Map<String,Marker> startMarkers = new HashMap<>();
    private Map<String,MarkerInfo> markerInfoMap = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        unbinder = ButterKnife.bind(this);
        XLog.init(BuildConfig.DEBUG ? LogLevel.ALL : LogLevel.NONE);
        mMapView = findViewById(R.id.mapView);
        mMapView.onCreate(savedInstanceState);
        MainActivityPermissionsDispatcher.requestPermissionWithCheck(this);
    }

    private void location() {
        //声明locationClient对象
        AMapLocationClient locationClient = new AMapLocationClient(this);
        //初始化定位参数
        //声明locationClientOption对象
        AMapLocationClientOption locationClientOption = new AMapLocationClientOption();
        //设置定位监听
        locationClient.setLocationListener(amapLocation -> {
            if (amapLocation != null) {
                if (amapLocation.getErrorCode() == 0) {
                    //定位成功回调信息，设置相关消息
                    double latitude = amapLocation.getLatitude();//获取纬度
                    double longitude = amapLocation.getLongitude();//获取经度
                    addLocationMarker(latitude, longitude);

                } else {
                    //显示错误信息ErrCode是错误码，errInfo是错误信息，详见错误码表。
                    XLog.e("location Error, ErrCode:"
                            + amapLocation.getErrorCode() + ", errInfo:"
                            + amapLocation.getErrorInfo());
                }
            }
        });
        //设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
        locationClientOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //设置定位间隔,单位毫秒,默认为2000ms
        locationClientOption.setInterval(10000);
        //设置定位参数
        locationClient.setLocationOption(locationClientOption);
        // 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
        // 注意设置合适的定位时间的间隔（最小间隔支持为1000ms），并且在合适时间调用stopLocation()方法来取消定位请求
        // 在定位结束后，在合适的生命周期调用onDestroy()方法
        // 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除
        //启动定位
        locationClient.startLocation();
    }

    // 使用图片资源添加定位marker
    private void addLocationMarker(double lat, double lon) {
        LatLng latLng = new LatLng(lat, lon);
        if (locationMarker == null) {
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.my_location);
            MarkerOptions markerOptions = new MarkerOptions().setFlat(true)
                    .anchor(0.5f, 0.5f)
                    .infoWindowEnable(false)
                    .position(latLng)
                    .icon(bitmapDescriptor);
            locationMarker = aMap.addMarker(markerOptions);
            aMap.moveCamera(CameraUpdateFactory.changeLatLng(latLng));
        } else {
            locationMarker.setPosition(latLng);
        }
    }

    // 使用图片资源添加起点marker
    private void addStartMarker(MarkerInfo markerInfo) {
        LatLng latLng = new LatLng(markerInfo.lat, markerInfo.lon);
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.start);
        MarkerOptions markerOptions = new MarkerOptions().setFlat(true)
                .anchor(0.5f, 0.5f)
                .infoWindowEnable(false)
                .position(latLng)
                .icon(bitmapDescriptor);
        startMarkers.put(markerInfo.info,aMap.addMarker(markerOptions));
    }

    // 自定义view添加marker
    private void addFlightMarker(MarkerInfo markerInfo) {
        String info = markerInfo.info;
        Marker marker = planeMarkers.get(info);
        LatLng latLng = new LatLng(markerInfo.lat, markerInfo.lon);
        if(marker == null){
            View v = getLayoutInflater().inflate(R.layout.info_window, null);
            TextView tvInfo = v.findViewById(R.id.tv_info);
            tvInfo.setText(info);
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromView(v);
            MarkerOptions markerOptions = new MarkerOptions().setFlat(true)
                    .anchor(0.5f, 0.5f)
//                    .autoOverturnInfoWindow(true)
                    .title(info)
                    .infoWindowEnable(false)
                    .position(latLng)
                    .icon(bitmapDescriptor);
            marker = aMap.addMarker(markerOptions);
        }else {
            marker.setPosition(latLng);
        }
        marker.setObject(markerInfo);
        planeMarkers.put(info,marker);
    }


    // 指南针旋转
    private void startIvCompass(float bearing) {
        bearing = 360 - bearing;
        XLog.d("startIvCompass: " + bearing);
        RotateAnimation rotateAnimation = new RotateAnimation(lastBearing, bearing, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setFillAfter(true);

        ivCompass.startAnimation(rotateAnimation);
        lastBearing = bearing;
    }

    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    })
    protected void requestPermission() {
        aMap = mMapView.getMap();
        aMap.setOnMarkerClickListener(marker -> {
            Object object = marker.getObject();
            if (object == null) {
                return false;
            }
            MarkerInfo markerInfo = (MarkerInfo) object;
            Toast.makeText(this,markerInfo.info,Toast.LENGTH_SHORT).show();
            return true;
        });
        aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {

                startIvCompass(cameraPosition.bearing);
            }

            @Override
            public void onCameraChangeFinish(CameraPosition cameraPosition) {
            }
        });
        //一般原生的样式都难以满足，需要自定义
        UiSettings mUiSettings = aMap.getUiSettings();
        // 设置缩放按钮是否可见
        mUiSettings.setZoomControlsEnabled(false);
        // 设置旋转手势是否可用
//        mUiSettings.setRotateGesturesEnabled(false);
        // 设置倾斜手势是否可用
//        mUiSettings.setTiltGesturesEnabled(false);
        location();
        cbPath.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (Marker marker : planeMarkers.values()) {
                marker.setVisible(isChecked);
            }

            for (Marker marker : startMarkers.values()) {
                marker.setVisible(isChecked);
            }

            for (Polyline polyline : flyPaths.values()) {
                polyline.setVisible(isChecked);
            }
            zoomToSpan();
        });

        //构造数据
        for (int i = 0; i < 3; i++) {
            MarkerInfo markerInfo = new MarkerInfo(Constant.lat + Math.abs(Math.random()) * i,
                    Constant.lon + Math.abs(Math.random()) * i,"p" + i);
            addStartMarker(markerInfo);
            addPolyline(markerInfo);
            markerInfoMap.put(markerInfo.info,markerInfo);
        }
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (isFinishing()) {
                    timer.cancel();
                }
                for (int i = 0; i < 3; i++) {
                    String info = "p" + i;
                    MarkerInfo lastInfo = markerInfoMap.get(info);
                    double v = Math.abs(Math.random());
                    MarkerInfo markerInfo = new MarkerInfo(lastInfo.lat + v,
                            lastInfo.lon  + v,info);
                    markerInfoMap.put(markerInfo.info,markerInfo);
                    addFlightMarker(markerInfo);
                    addPolyline(markerInfo);
                    zoomToSpan();
                }
            }
        },5000,5000);
    }

    /**
     * 缩放移动地图，保证所有自定义marker在可视范围中。
     */
    public void zoomToSpan() {
        List<LatLng> pointList = new ArrayList<>();

        if (cbPath.isChecked()) {
            for (Polyline polyline : flyPaths.values()) {
                pointList.addAll(polyline.getPoints());
            }
        }

        LatLng centerPoint = null;

        /*if (locationMarker != null) {
            centerPoint = locationMarker.getPosition();
        }*/
        if (pointList.isEmpty()) {
            return;
        }

        LatLngBounds bounds;
        if (centerPoint == null) {
            bounds = getLatLngBounds(pointList);
        }else {
            bounds = getLatLngBounds(centerPoint, pointList);
        }
        aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150));
    }

    //根据中心点和自定义内容获取缩放bounds
    private LatLngBounds getLatLngBounds(LatLng centerPoint, List<LatLng> pointList) {
        LatLngBounds.Builder b = LatLngBounds.builder();
        if (centerPoint != null){
            for (int i = 0; i < pointList.size(); i++) {
                LatLng p = pointList.get(i);
                LatLng p1 = new LatLng((centerPoint.latitude * 2) - p.latitude, (centerPoint.longitude * 2) - p.longitude);
                b.include(p);
                b.include(p1);
            }
        }
        return b.build();
    }

    // 添加轨迹
    private void addPolyline(List<MarkerInfo> markerInfoList) {
        for (MarkerInfo info : markerInfoList) {
            if (info.lat == 0 && info.lon == 0) {
                continue;
            }
            LatLng latLng = new LatLng(info.lat, info.lon);
            String key = info.info;
            Polyline polyline = flyPaths.get(key);
            if (polyline == null) {
                List<LatLng> latLngList = new ArrayList<>(1);
                latLngList.add(latLng);
                polyline = addPolyline(latLngList, false, Color.parseColor("#6796F3"), 10);
                polyline.setVisible(cbPath.isChecked());
                flyPaths.put(key, polyline);
            } else {
                List<LatLng> points = polyline.getPoints();
                points.add(latLng);
                polyline.setPoints(points);
            }
        }
    }

    // 添加轨迹
    private void addPolyline(MarkerInfo info) {
        if (info.lat == 0 && info.lon == 0) {
            return;
        }
        LatLng latLng = new LatLng(info.lat, info.lon);
        String key = info.info;
        Polyline polyline = flyPaths.get(key);
        if (polyline == null) {
            polyline = addPolyline(latLng, false, Color.parseColor("#6796F3"), 10);
            polyline.setVisible(cbPath.isChecked());
            flyPaths.put(key, polyline);
        } else {
            List<LatLng> points = polyline.getPoints();
            points.add(latLng);
            polyline.setPoints(points);
        }
    }

    // 添加折线
    private Polyline addPolyline(List<LatLng> latLngList, boolean dotted, int color, int width) {
        return aMap.addPolyline((new PolylineOptions())
                .addAll(latLngList)
                .width(width)
                .setDottedLine(dotted)
                .color(color));
    }

    // 添加折线
    private Polyline addPolyline(LatLng latLng, boolean dotted, int color, int width) {
        return aMap.addPolyline((new PolylineOptions())
                .add(latLng)
                .width(width)
                .setDottedLine(dotted)
                .color(color));
    }

    /**
     * 根据自定义内容获取缩放bounds
     */
    private LatLngBounds getLatLngBounds(List<LatLng> pointList) {
        LatLngBounds.Builder b = LatLngBounds.builder();
        for (int i = 0; i < pointList.size(); i++) {
            LatLng p = pointList.get(i);
            b.include(p);
        }
        return b.build();
    }


    @OnPermissionDenied({Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    })
    void showDenied() {
        MainActivityPermissionsDispatcher.requestPermissionWithCheck(this);
    }

    @OnNeverAskAgain({Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    })
    void alwaysDenied() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("提示")
                .setMessage("需要赋予访问存储和定位的权限，请到“设置”>“应用”>“权限”中配置权限。")
                .setNegativeButton("取消", (dialog, which) ->
                        dialog.cancel()).setPositiveButton("确定",
                (dialog, which) -> {
                    dialog.cancel();
                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
        unbinder.unbind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        mMapView.onPause();
    }


    @OnClick({R.id.iv_compass, R.id.iv_location})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.iv_compass:
                aMap.moveCamera(CameraUpdateFactory.changeBearing(0));
                break;
            case R.id.iv_location:
                if (locationMarker != null) {
                    // 直接定位都某点的方法
                    aMap.moveCamera(CameraUpdateFactory.changeLatLng(locationMarker.getPosition()));
                }
                break;
        }
    }
}
