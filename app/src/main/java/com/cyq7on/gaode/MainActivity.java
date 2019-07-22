package com.cyq7on.gaode;

import android.Manifest;
import android.content.Intent;
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
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;

import org.jokar.permissiondispatcher.annotation.NeedsPermission;
import org.jokar.permissiondispatcher.annotation.OnNeverAskAgain;
import org.jokar.permissiondispatcher.annotation.OnPermissionDenied;
import org.jokar.permissiondispatcher.annotation.RuntimePermissions;

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
        // 设置缩放按钮是否可见，
        mUiSettings.setZoomControlsEnabled(false);
        // 设置定位按钮是否可见
        mUiSettings.setRotateGesturesEnabled(false);
        // 设置倾斜手势是否可用
//        mUiSettings.setTiltGesturesEnabled(true);
        location();
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
                    aMap.moveCamera(CameraUpdateFactory.changeLatLng(locationMarker.getPosition()));
                }
                break;
        }
    }
}
