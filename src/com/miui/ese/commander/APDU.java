package com.miui.ese.commander;

import java.util.HashSet;
import java.util.Set;

public class APDU {

    public static final String DEFAULT_STATUS = "9000";

    String apdu;

    Set<String> expectedStatus;

    public static APDU parse(String line) {
        line = line.trim();
        String[] fields = line.split("\\s");
        String apduPart;
        String statusPart;
        if (fields.length > 0) {
            apduPart = fields[0];
            statusPart = line.substring(apduPart.length()).trim();
        } else {
            apduPart = line;
            statusPart = DEFAULT_STATUS;
        }
        String[] statusList = statusPart.split(",\\s*");
        HashSet<String> statusSet = new HashSet<String>();
        for (String s : statusList) {
            statusSet.add(s.toUpperCase());
        }

        APDU apdu = new APDU();
        apdu.apdu = apduPart;
        apdu.expectedStatus = statusSet;

        return apdu;
    }
}
