package com.miui.ese.commander;

import com.android.nfc_extras.NfcExecutionEnvironment;
import com.android.nfc_extras.NfcAdapterExtras;
import android.nfc.NfcAdapter;
import android.util.Log;

public class NfcSecureElement implements INfcSecureElement {

    private static final String TAG = "NfcSecureElement";

    private NfcExecutionEnvironment nfcEnv;

    private NfcAdapter nfcAdapter;

    public NfcSecureElement(NfcAdapter adapter) {
        this.nfcAdapter = adapter;
    }

    @Override
    public synchronized void closeSecureElementConnection() {
        if (nfcEnv == null) {
            Log.e(TAG, "ese is not open");
            return;
        }

        try {
            nfcEnv.close();
        } catch (Exception e) {
            Log.e(TAG, "error when close ese", e);
        }
        nfcEnv = null;
    }

    @Override
    public synchronized byte[] exchangeAPDU(byte[] apdu) {
        if (nfcEnv == null) {
            Log.e(TAG, "ese is not open");
            return null;
        }

        Log.i(TAG, "send apdu: " + HexDump.toHexString(apdu));
        byte[] result = null;
        try {
            result = nfcEnv.transceive(apdu);
        } catch (Exception e) {
            Log.e(TAG, "error when exchange apdu", e);
        }
        if (result != null) {
            Log.i(TAG, "resp apdu: " + HexDump.toHexString(result));
        } else {
            Log.i(TAG, "resp apdu is null");
        }

        return result;
    }

    @Override
    public synchronized void openSecureElementConnection() {
        if (nfcEnv != null) {
            Log.e(TAG, "ese has opened, ignore open");
            return;
        }
        NfcAdapterExtras nfcAdapterExtras = NfcAdapterExtras.get(nfcAdapter);
        try {
            nfcEnv = nfcAdapterExtras.getEmbeddedExecutionEnvironment();
            nfcEnv.open();
        } catch (Exception e) {
            Log.e(TAG, "error when open ese", e);
        }
    }

}