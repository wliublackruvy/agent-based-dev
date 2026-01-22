// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.common.util;

public final class PhoneMasker {

    private PhoneMasker() {
    }

    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
