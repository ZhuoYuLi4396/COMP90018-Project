package unimelb.comp90018.equaltrip;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ProfileActivity extends AppCompatActivity {

    private SwitchMaterial swLocation, swNotification, swBluetooth, swCamera;
    private TextView tvCurrencyValue;

    private static final int RC_LOCATION = 101;
    private static final int RC_NOTI = 102;
    private static final int RC_BT = 103;
    private static final int RC_CAMERA = 104;

    private String currency = "AUD Australian Dollar";
    private final String[] currencies = new String[]{
            "AUD Australian Dollar","USD United States Dollar","EUR Euro","CNY Chinese Yuan"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        swLocation = findViewById(R.id.swLocation);
        swNotification = findViewById(R.id.swNotification);
        swBluetooth = findViewById(R.id.swBluetooth);
        swCamera = findViewById(R.id.swCamera);
        tvCurrencyValue = findViewById(R.id.tvCurrencyValue);
        LinearLayout rowCurrency = findViewById(R.id.rowCurrency);
        BottomNavigationView bottom = findViewById(R.id.bottomNav);

        // 初始化开关
        swLocation.setChecked(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION));
        if (Build.VERSION.SDK_INT >= 33) {
            swNotification.setChecked(hasPermission(Manifest.permission.POST_NOTIFICATIONS));
        } else swNotification.setChecked(true);
        if (Build.VERSION.SDK_INT >= 31) {
            swBluetooth.setChecked(hasPermission(Manifest.permission.BLUETOOTH_CONNECT));
        } else swBluetooth.setChecked(true);
        swCamera.setChecked(hasPermission(Manifest.permission.CAMERA));

        // 货币
        tvCurrencyValue.setText(currency);
        rowCurrency.setOnClickListener(v -> showCurrencyDialog());

        // 监听
        swLocation.setOnCheckedChangeListener((b, c) -> {
            if (c) requestIfNeeded(Manifest.permission.ACCESS_FINE_LOCATION, RC_LOCATION);
            else toast("Location usage turned off in app");
        });
        swNotification.setOnCheckedChangeListener((b, c) -> {
            if (Build.VERSION.SDK_INT >= 33) {
                if (c) requestIfNeeded(Manifest.permission.POST_NOTIFICATIONS, RC_NOTI);
                else toast("Notifications turned off in app");
            } else toast("Notifications preference updated");
        });
        swBluetooth.setOnCheckedChangeListener((b, c) -> {
            if (Build.VERSION.SDK_INT >= 31) {
                if (c) requestIfNeeded(Manifest.permission.BLUETOOTH_CONNECT, RC_BT);
                else toast("Bluetooth usage turned off in app");
            } else toast("Bluetooth preference updated");
        });
        swCamera.setOnCheckedChangeListener((b, c) -> {
            if (c) requestIfNeeded(Manifest.permission.CAMERA, RC_CAMERA);
            else toast("Camera usage turned off in app");
        });

        // ===== BottomNav：Profile -> Home / Trips =====
        if (bottom != null) {
            bottom.setSelectedItemId(R.id.nav_profile);       // 高亮 Profile
            bottom.setOnItemReselectedListener(item -> {});   // 避免重复触发

            bottom.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    Intent i = new Intent(ProfileActivity.this, HomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(i);
                    overridePendingTransition(0, 0);
                    finish(); // 想保留返回栈可删掉
                    return true;
                } else if (id == R.id.nav_trips) {
                    Toast.makeText(this, "Trips coming soon", Toast.LENGTH_SHORT).show();
                    bottom.setSelectedItemId(R.id.nav_profile); // 保持选中 Profile
                    return false;
                } else if (id == R.id.nav_profile) {
                    return true; // 已在本页
                }
                return false;
            });
        }
    }

    private boolean hasPermission(String perm) {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestIfNeeded(String perm, int rc) {
        if (!hasPermission(perm) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{perm}, rc);
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void showGoToSettingsDialog(String msg) {
        new AlertDialog.Builder(this)
                .setMessage(msg + "\nYou can revoke it in system Settings.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(i);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCurrencyDialog() {
        int selected = 0;
        for (int i = 0; i < currencies.length; i++) if (currencies[i].equals(currency)) { selected = i; break; }
        final int[] tmp = {selected};
        new AlertDialog.Builder(this)
                .setTitle("Choose currency")
                .setSingleChoiceItems(currencies, selected, (d, which) -> tmp[0] = which)
                .setPositiveButton("OK", (d, w) -> {
                    currency = currencies[tmp[0]];
                    tvCurrencyValue.setText(currency);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        boolean granted = r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED;
        if (rc == RC_LOCATION) {
            swLocation.setChecked(granted);
            if (!granted) toast("Location permission denied");
        } else if (rc == RC_NOTI) {
            swNotification.setChecked(granted || Build.VERSION.SDK_INT < 33);
            if (!granted && Build.VERSION.SDK_INT >= 33) toast("Notification permission denied");
        } else if (rc == RC_BT) {
            swBluetooth.setChecked(granted || Build.VERSION.SDK_INT < 31);
            if (!granted && Build.VERSION.SDK_INT >= 31) toast("Bluetooth permission denied");
        } else if (rc == RC_CAMERA) {
            swCamera.setChecked(granted);
            if (!granted) toast("Camera permission denied");
        }
    }
}
