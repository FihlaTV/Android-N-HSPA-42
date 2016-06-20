package n.android_n_telephony_bug;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NTWRK_INFO";
    private static final int REQ_PERMISSION_LOCATION = 0;

    /**
     * Hash map that converts integer representation of network type to string
     */
    private HashMap<Integer, String> mNetworkTypeMap;

    /**
     * String that is used as cache for all outputs from called methods
     */
    private String mLogCache;

    /**
     * Button that you must press to refresh all data
     * They will be logged into logcat + shown on the screen of your device
     *
     * Change method {@link #debug(String)} to change behaviour.
     */
    private Button mRefreshButton;

    /**
     * TextView into which the output from this sample
     * will be written
     */
    private TextView mConsoleOutput;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConsoleOutput = (TextView) findViewById(R.id.console_output);
        mRefreshButton = (Button) findViewById(R.id.refresh_button);
        mRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                reloadAll();
            }
        });

        fillNetworkTypeMap ();
        reloadAll ();
    }

    /**
     * Checks for permissions and if they are granted then it will try to
     * check if your device is connected to HSPA+42 or LTE-A
     */
    private void reloadAll() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERMISSION_LOCATION);
        } else {
            mLogCache = ""; // clear cache
            init();
        }
    }

    /**
     * Fills map which converts {@link TelephonyManager#getNetworkType()} into human-readable string
     * Those readable string are taken from for example
     * {@link TelephonyManager#NETWORK_TYPE_UNKNOWN},
     * {@link TelephonyManager#NETWORK_TYPE_GPRS},
     * {@link TelephonyManager#NETWORK_TYPE_EDGE},
     * etc...
     */
    private void fillNetworkTypeMap() {
        mNetworkTypeMap = new HashMap<>();
        mNetworkTypeMap.put(0, "UNKNOWN");
        mNetworkTypeMap.put(1, "2G/GPRS");
        mNetworkTypeMap.put(2, "2G/EDGE");
        mNetworkTypeMap.put(3, "3G/UMTS");
        mNetworkTypeMap.put(4, "3G/CDMA");
        mNetworkTypeMap.put(5, "3G/EVDO 0");
        mNetworkTypeMap.put(6, "3G/EVDO A");
        mNetworkTypeMap.put(7, "3G/1xRTT");
        mNetworkTypeMap.put(8, "3G/HSDPA");
        mNetworkTypeMap.put(9, "3G/HSUPA");
        mNetworkTypeMap.put(10, "3G/HSPA");
        mNetworkTypeMap.put(11, "iDen");
        mNetworkTypeMap.put(12, "3G/EVDO B");
        mNetworkTypeMap.put(13, "4G/LTE");
        mNetworkTypeMap.put(14, "3G/eHRPD");
        mNetworkTypeMap.put(15, "3G/HSPA+");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQ_PERMISSION_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                reloadAll();
            } else {
                // I hope there is no need to handle this on sample project... Hope you are smart enough :)
                debug("You must grant location permission to proceed");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Everything was tested on Nexus 6P (angler).
     * <p/>
     * This sample just initializes everything. Bugs are listed below in other methods.
     */
    private void init() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE); // get instance of TelephonyManager

        List<CellInfo> cells = telephonyManager.getAllCellInfo(); // get all cells
        List<CellInfoWcdma> wcdmaCells = new ArrayList<>();
        List<CellInfoLte> lteCells = new ArrayList<>();

        if (cells != null && !cells.isEmpty()) {
            for (CellInfo cell : cells) {
                if (cell != null) {
                    if (cell instanceof CellInfoWcdma) { // get just 3G/WCDMA cells
                        wcdmaCells.add((CellInfoWcdma) cell);

                    } else if (cell instanceof CellInfoLte) { // get just 4G/LTE cells
                        lteCells.add((CellInfoLte) cell);
                    }
                }
            }
        }

        logNetworkType (telephonyManager);

        if (!wcdmaCells.isEmpty()) {
            debug("Connected to 3G network");
            checkForHspa42Availability (wcdmaCells);
        }

        if (!lteCells.isEmpty()) {
            debug("Connected to 4G network");
            checkForLteAdvancedAvailability (lteCells);
        }

        mConsoleOutput.setText(mLogCache); // write into TextView
    }

    /**
     * Logs current network type which is taken from Android API in human readable form
     *
     * @param telephonyManager instance of {@link TelephonyManager}
     */
    private void logNetworkType(@NonNull  TelephonyManager telephonyManager) {
        debug("Android framework network type - " + mNetworkTypeMap.get(telephonyManager.getNetworkType()));
    }


    /**
     * This method will check if phone is currently connected to HSPA+ 42
     * It might not work in 100% cause it depends on {@param wcdmaCells} which was
     * generated using Android public API. If method {@link TelephonyManager#getAllCellInfo()}
     * does not return list of ALL cells then it might not work
     *
     * @param wcdmaCells list of 3G/WCDMA cells that our device sees
     */
    private void checkForHspa42Availability(@NonNull List<CellInfoWcdma> wcdmaCells) {
        CellIdentityWcdma servingCell = null; // expecting that only one cell is serving, dual sim phones might have more serving cells but Nexus 6P is not dual sim...
        CellIdentityWcdma siblingCell = null; // Sibling to serving - that one which turns HSPA+ into HSPA+42

        // Find serving cell
        Iterator<CellInfoWcdma> iterator = wcdmaCells.iterator();

        while (iterator.hasNext()) {
            CellInfoWcdma cell = iterator.next();
            if (cell.isRegistered()) {
                servingCell = cell.getCellIdentity();
                iterator.remove(); // remove current cell from list cause we will search for cells with same PSC
                debug("Serving cell ... PSC " + servingCell.getPsc() + ", UARFCN " + servingCell.getUarfcn());

                break;
            }
        }

        if (servingCell != null) {
            for (CellInfoWcdma cell : wcdmaCells) {
                if (cell.getCellIdentity().getPsc() == servingCell.getPsc()) {
                    siblingCell = cell.getCellIdentity();
                    debug("Sibling cell ... PSC " + siblingCell.getPsc() + ", UARFCN " + siblingCell.getUarfcn());
                    break;
                }
            }

            if (siblingCell != null) {
                debug("✓ Probably running on HSPA+ 42 network");
                debug("✓ Carrier aggregation of " + servingCell.getUarfcn() + " + " + siblingCell.getUarfcn());

            } else {
                debug("✖ No sibling cell found");
                debug("✖ This might be HSPA+42 network or not");
            }

        } else {
            debug("Error - API says that no cell is serving, this should not happen");
        }
    }

    /**
     * This method is similar to {@link #checkForHspa42Availability(List)}
     * It works nearly as the other one - its just optimized for LTE instead of WCDMA...
     * @param lteCells list of 4G/LTE cells that our device sees
     */
    private void checkForLteAdvancedAvailability(@NonNull List<CellInfoLte> lteCells) {
        CellIdentityLte servingCell = null; // expecting that only one cell is serving, dual sim phones might have more serving cells but Nexus 6P is not dual sim...
        CellIdentityLte siblingCell = null; // Sibling to serving - that one which turns HSPA+ into HSPA+42

        // Find serving cell
        Iterator<CellInfoLte> iterator = lteCells.iterator();

        while (iterator.hasNext()) {
            CellInfoLte cell = iterator.next();
            if (cell.isRegistered()) {
                servingCell = cell.getCellIdentity();
                iterator.remove(); // remove current cell from list cause we will search for cells with same PSC
                debug("Serving cell ... PCI " + servingCell.getPci() + ", EARFCN " + servingCell.getEarfcn());

                break;
            }
        }

        if (servingCell != null) {
            for (CellInfoLte cell : lteCells) {
                if (cell.getCellIdentity().getPci() == servingCell.getPci()) {
                    siblingCell = cell.getCellIdentity();
                    debug("Sibling cell ... PCI " + siblingCell.getPci() + ", EARFCN " + siblingCell.getEarfcn());
                    break;
                }
            }

            if (siblingCell != null) {
                debug("✓ Probably running on LTE-A network");
                debug("✓ Carrier aggregation of " + servingCell.getEarfcn() + " + " + siblingCell.getEarfcn());

            } else {
                debug("✖ No sibling cell found");
                debug("✖ This might be LTE-A network or not");
            }

        } else {
            debug("Error - API says that no cell is serving, this should not happen");
        }
    }

    /**
     * @param message message which should be sent to logcat
     */
    private void debug(String message) {
        Log.d(TAG, message);
        mLogCache += message + "\n";
    }


}
