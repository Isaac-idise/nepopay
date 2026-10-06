package com.nepopay.user;

/** Stores every Nigerian mobile number in one local form (0XXXXXXXXXX) so +234 and 0 versions can't both register. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        String p = raw.trim();
        if (p.startsWith("+234")) {
            return "0" + p.substring(4);
        }
        return p;
    }
}
