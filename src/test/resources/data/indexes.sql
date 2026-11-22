-- Optional indexes for the application's queries. Safe to run more than once; changes no data.

-- Replays a user's FILLED and NEW orders in time order (orders and portfolio).
CREATE INDEX IF NOT EXISTS orders_user_status_datetime_idx
    ON orders (userId, status, datetime, id);

-- Finds the latest quote of each instrument (MARKET price and portfolio valuation).
CREATE INDEX IF NOT EXISTS marketdata_instrument_date_idx
    ON marketdata (instrumentId, date DESC, id DESC);
