package app.reloop.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    private static final double DELTA = 0.5;

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        double km = GeoUtils.distanceKm(0, 0, 1, 0);
        assertEquals(111.19, km, DELTA);
    }

    @Test
    void samePointIsZero() {
        assertEquals(0.0, GeoUtils.distanceKm(17.385, 78.4867, 17.385, 78.4867), 0.0001);
    }

    @Test
    void hyderabadToSecunderabadIsPlausible() {
        double km = GeoUtils.distanceKm(17.3850, 78.4867, 17.4399, 78.4983);
        assertTrue(km > 4 && km < 8, "expected ~5-7 km, got " + km);
    }

    @Test
    void symmetricDistance() {
        double a = GeoUtils.distanceKm(17.385, 78.4867, 13.0827, 80.2707);
        double b = GeoUtils.distanceKm(13.0827, 80.2707, 17.385, 78.4867);
        assertEquals(a, b, 0.0001);
    }

    @Test
    void toDoubleUsesFallbackForNull() {
        assertEquals(5.0, GeoUtils.toDouble(null, 5.0));
        assertEquals(2.5, GeoUtils.toDouble(new BigDecimal("2.5"), 5.0));
    }
}
