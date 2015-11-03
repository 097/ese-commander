package com.miui.ese.commander;

import android.util.Log;

public class DummyNfcSecureElement implements INfcSecureElement {

    private static final String TAG = "DummyNfcSecureElement";

    private static final byte[] STATUS_OK = {
            (byte) 0x90, (byte) 0x00,
    };

    @Override
    public void closeSecureElementConnection() {
        Log.i(TAG, "ese connection closed");
    }

    @Override
    public byte[] exchangeAPDU(byte[] apdu) {
        Log.i(TAG, "exchange apdu: " + HexDump.toHexString(apdu));
        return STATUS_OK;
    }

    @Override
    public void openSecureElementConnection() {
        Log.i(TAG, "ese connection opened");
    }
}
