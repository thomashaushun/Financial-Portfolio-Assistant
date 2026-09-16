# Testing
# Running unit tests and check coverage

## Running test files:

## Checking coverage:

# Endpoints for testing services

## Testing recommendation
http://localhost:8080/api/recommendation?symbol=AAPL&interval=1day&outputsize=60

## Testing Dashboard
http://localhost:8080/api/dashboard?symbol=AAPL&interval=1day&outputsize=60

Should contain:
- quote 
- timeSeries
- features
- backtest
- recommendation