package com.firetracker.projection;

/**
 * One checkpoint of the simulated fan: the p10/p50/p90 of the path values at
 * {@code monthsFromStart} months into the projection. Still in {@code double}; the service
 * rounds these to SGD cents and attaches calendar dates for the API response.
 */
record SimulatedBand(int monthsFromStart, double p10, double p50, double p90) {
}
