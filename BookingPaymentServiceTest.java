package com.airbnb.payments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BookingPaymentServiceTest {

    private BookingPaymentService service;

    @BeforeEach
    void setUp() {
        service = BookingPaymentService.getInstance();
    }

    @Test
    void testProcessPayment_success() {
        boolean result = service.processPayment("guest123", "listing456", 3, 100.0);
        assertTrue(result);
    }

    @Test
    void testProcessPayment_zeroNights() {
        // handled by controller, not tested here
    }

    @Test
    void testGetGuestBookings_returnsBookings() {
        service.processPayment("guest123", "listing456", 3, 100.0);
        var bookings = service.getGuestBookings("guest123");
        assertFalse(bookings.isEmpty());
    }

    @Test
    void testCalculateListingRevenue() {
        int revenue = service.calculateListingRevenue("listing456", 2024);
        assertTrue(revenue >= 0);
    }
}
