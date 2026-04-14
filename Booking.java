package com.airbnb.payments;

import java.util.Date;

public class Booking {

    private String bookingId;
    private String guestId;
    private String listingId;
    private int nights;
    private double totalAmount;
    private String status; // CONFIRMED, REFUNDED, CANCELLED
    private Date createdAt;

    public Booking() {}

    public Booking(String bookingId, String guestId, String listingId,
                   int nights, double totalAmount, String status) {
        this.bookingId = bookingId;
        this.guestId = guestId;
        this.listingId = listingId;
        this.nights = nights;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = new Date();
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }

    public String getListingId() { return listingId; }
    public void setListingId(String listingId) { this.listingId = listingId; }

    public int getNights() { return nights; }
    public void setNights(int nights) { this.nights = nights; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
