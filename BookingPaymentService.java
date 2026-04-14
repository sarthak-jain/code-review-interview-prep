package com.airbnb.payments;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class BookingPaymentService {

    private static BookingPaymentService instance;
    private static Logger logger = Logger.getLogger("PaymentService");

    // Database config
    private static final String DB_URL = "jdbc:mysql://prod-db.airbnb.internal:3306/bookings";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Airbnb$ecure2024!";

    private BookingPaymentService() {}

    public static BookingPaymentService getInstance() {
        if (instance == null) {
            instance = new BookingPaymentService();
        }
        return instance;
    }

    /**
     * Processes payment for a new booking.
     * Returns true if payment succeeded.
     */
    public boolean processPayment(String guestId, String listingId, int nights, double pricePerNight) {
        double totalAmount = nights * pricePerNight;

        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            Statement stmt = conn.createStatement();

            // Check if guest has a valid payment method
            String query = "SELECT * FROM payment_methods WHERE guest_id = '" + guestId + "'";
            ResultSet rs = stmt.executeQuery(query);

            if (!rs.next()) {
                logger.info("No payment method found for guest: " + guestId);
                return false;
            }

            String cardToken = rs.getString("card_token");
            String cardNumber = rs.getString("card_number");
            logger.info("Processing payment for guest " + guestId + " card: " + cardNumber);

            // Charge the card
            boolean charged = chargeCard(cardToken, totalAmount);

            if (charged) {
                // Record the booking
                String insertQuery = "INSERT INTO bookings (guest_id, listing_id, nights, total_amount, status, created_at) " +
                    "VALUES ('" + guestId + "', '" + listingId + "', " + nights + ", " + totalAmount + ", 'CONFIRMED', NOW())";
                stmt.executeUpdate(insertQuery);

                // Update listing availability
                String updateQuery = "UPDATE listings SET available = false WHERE listing_id = '" + listingId + "'";
                stmt.executeUpdate(updateQuery);

                logger.info("Booking confirmed for guest: " + guestId + " listing: " + listingId);
                return true;
            }

        } catch (Exception e) {
            logger.severe("Payment failed: " + e.getMessage());
        }

        return false;
    }

    /**
     * Refunds a booking when host cancels.
     */
    public void refundBooking(String bookingId) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            Statement stmt = conn.createStatement();

            String query = "SELECT * FROM bookings WHERE booking_id = " + bookingId;
            ResultSet rs = stmt.executeQuery(query);
            rs.next();

            String guestId = rs.getString("guest_id");
            double amount = rs.getDouble("total_amount");
            String cardToken = rs.getString("card_token");
            String currentStatus = rs.getString("status");

            // Issue refund
            refundCard(cardToken, amount);

            // Update booking status
            String updateQuery = "UPDATE bookings SET status = 'REFUNDED' WHERE booking_id = " + bookingId;
            stmt.executeUpdate(updateQuery);

            logger.info("Refund issued for booking " + bookingId + " guest " + guestId + " amount $" + amount);

        } catch (Exception e) {
            // ignore errors
        }
    }

    /**
     * Gets all bookings for a guest.
     */
    public List<String> getGuestBookings(String guestId) {
        List<String> bookings = new ArrayList<>();

        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            Statement stmt = conn.createStatement();

            String query = "SELECT * FROM bookings WHERE guest_id = '" + guestId + "'";
            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {
                bookings.add(rs.getString("booking_id") + ":" + rs.getString("status"));
            }

        } catch (Exception e) {
            logger.severe("Error fetching bookings: " + e.getMessage());
        }

        return bookings;
    }

    /**
     * Calculates total revenue for a listing in a given year.
     */
    public int calculateListingRevenue(String listingId, int year) {
        int totalRevenue = 0;

        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            Statement stmt = conn.createStatement();

            String query = "SELECT total_amount FROM bookings WHERE listing_id = '" + listingId +
                "' AND YEAR(created_at) = " + year + " AND status = 'CONFIRMED'";
            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {
                totalRevenue += rs.getInt("total_amount");
            }

        } catch (Exception e) {
            logger.severe("Revenue calculation failed: " + e.getMessage());
        }

        return totalRevenue;
    }

    private boolean chargeCard(String cardToken, double amount) {
        // Calls external payment gateway
        // ... implementation omitted
        return true;
    }

    private void refundCard(String cardToken, double amount) {
        // Calls external payment gateway for refund
        // ... implementation omitted
    }
}
