package com.miui.ese.commander;

public interface INfcSecureElement {
    void closeSecureElementConnection();

    byte[] exchangeAPDU(byte[] apdu);

    void openSecureElementConnection();
}
