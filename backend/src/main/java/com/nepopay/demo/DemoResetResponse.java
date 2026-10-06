package com.nepopay.demo;

/** adjustmentReference is null when the balance was already at the demo amount. */
public record DemoResetResponse(long balanceKobo, String adjustmentReference) {
}
