# PR: Instant Booking Payment Processing

## What does this PR do?

Adds payment processing support for the Instant Book feature. When a guest books a listing, we charge their saved payment method immediately. Also handles refunds when a host cancels a confirmed booking.

## Endpoints added

- `POST /api/payments/book` — charge guest and confirm booking
- `POST /api/payments/refund/{bookingId}` — refund guest on host cancellation
- `GET /api/payments/bookings` — fetch booking history for a guest
- `GET /api/payments/revenue` — calculate total revenue for a listing

## Notes

- Uses the existing payment gateway (chargeCard / refundCard)
- Booking status flows: `CONFIRMED` → `REFUNDED`
- Revenue calculation is used by the host dashboard
