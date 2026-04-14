package com.airbnb.payments;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private BookingPaymentService paymentService = BookingPaymentService.getInstance();

    @PostMapping("/book")
    public String bookListing(@RequestParam String guestId,
                               @RequestParam String listingId,
                               @RequestParam int nights,
                               @RequestParam double pricePerNight) {

        if (nights <= 0) {
            return "Invalid number of nights";
        }

        boolean success = paymentService.processPayment(guestId, listingId, nights, pricePerNight);

        if (success) {
            return "Booking confirmed!";
        } else {
            return "Payment failed. Please try again.";
        }
    }

    @PostMapping("/refund/{bookingId}")
    public String refundBooking(@PathVariable String bookingId) {
        paymentService.refundBooking(bookingId);
        return "Refund processed";
    }

    @GetMapping("/bookings")
    public List<String> getBookings(@RequestParam String guestId) {
        return paymentService.getGuestBookings(guestId);
    }

    @GetMapping("/revenue")
    public int getRevenue(@RequestParam String listingId, @RequestParam int year) {
        return paymentService.calculateListingRevenue(listingId, year);
    }
}
