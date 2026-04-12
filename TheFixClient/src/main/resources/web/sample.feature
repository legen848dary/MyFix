Feature: FIX Order Sending and Verification

  # This sample feature file demonstrates all supported step definitions for
  # the TheFixClient Scenario Runner.
  #
  # Ensure the FIX simulator is running and the session profile is configured
  # before executing these scenarios.

  Background:
    Given the FIX session is connected

  # ---------------------------------------------------------------------------
  # Single order – Market
  # ---------------------------------------------------------------------------

  Scenario: Send a single market buy order
    When I send a New Order Single for 100 shares of "AAPL"
    Then the sent orders count should be at least 1
    And the order blotter should contain at least 1 order

  # ---------------------------------------------------------------------------
  # Single order – Limit (buy and sell)
  # ---------------------------------------------------------------------------

  Scenario: Send a single limit buy order
    When I send a New Order Single to buy 200 shares of "MSFT" at 415.50
    Then the sent orders count should be at least 1
    And the order blotter should contain an order with symbol "MSFT"

  Scenario: Send a single limit sell order
    When I send a New Order Single to sell 150 shares of "NVDA" at 875.00
    Then the sent orders count should be at least 1

  Scenario: Send a single sell-short order
    When I send a New Order Single to sell short 100 shares of "AMZN" at 200.00
    Then the sent orders count should be at least 1

  # ---------------------------------------------------------------------------
  # Single order – Stop and Stop-Limit
  # ---------------------------------------------------------------------------

  Scenario: Send a stop order
    When I send a STOP order to buy 100 shares of "AAPL" at stop 172.50
    Then the sent orders count should be at least 1

  Scenario: Send a stop-limit order
    When I send a STOP_LIMIT order to buy 100 shares of "IBM" at limit 145.00 stop 144.00
    Then the sent orders count should be at least 1

  # ---------------------------------------------------------------------------
  # Single order – Exotic types
  # ---------------------------------------------------------------------------

  Scenario: Send a market-on-close order
    When I send a New Order Single to buy 100 shares of "GS" as MARKET_ON_CLOSE order
    Then the sent orders count should be at least 1

  Scenario: Send a limit-on-close order
    When I send a New Order Single to buy 100 shares of "KO" as LIMIT_ON_CLOSE order
    Then the sent orders count should be at least 1

  # ---------------------------------------------------------------------------
  # Single order – Time-in-Force variations
  # ---------------------------------------------------------------------------

  Scenario: Send orders with all supported time-in-force values
    When I send a BUY New Order Single for 100 shares of "AAPL" at 170.00 with TIF DAY
    And  I send a BUY New Order Single for 100 shares of "AAPL" at 170.00 with TIF IOC
    And  I send a BUY New Order Single for 100 shares of "AAPL" at 170.00 with TIF FOK
    And  I send a BUY New Order Single for 100 shares of "AAPL" at 170.00 with TIF GTC
    And  I send a BUY New Order Single for 100 shares of "AAPL" at 170.00 with TIF OPG
    Then the sent orders count should be at least 5

  # ---------------------------------------------------------------------------
  # Single order – Market selection
  # ---------------------------------------------------------------------------

  Scenario: Send an order on the London Stock Exchange
    When I send a New Order Single for 100 shares of "BP.L" on market "XLON"
    Then the sent orders count should be at least 1
    And the order blotter should contain an order with symbol "BP.L"

  Scenario: Send an order on the Tokyo Stock Exchange
    When I send a New Order Single for 100 shares of "7203.T" on market "XTKS"
    Then the sent orders count should be at least 1

  # ---------------------------------------------------------------------------
  # Bulk flow – Fixed rate (continuous)
  # ---------------------------------------------------------------------------

  Scenario: Run a continuous fixed rate bulk flow and stop it
    When I start a fixed rate bulk flow at 10 orders per second
    Then the bulk flow should be running
    When I wait 2 seconds
    And  I stop the bulk order flow
    Then the bulk flow should not be running
    And  the sent orders count should be at least 1

  # ---------------------------------------------------------------------------
  # Bulk flow – Fixed rate (with total order count)
  # ---------------------------------------------------------------------------

  Scenario: Run a fixed rate bulk flow for a fixed number of orders
    When I start a fixed rate bulk flow of 10 total orders at 5 orders per second
    Then the bulk flow should be running
    When I wait 3 seconds
    And  I stop the bulk order flow
    Then the bulk flow should not be running
    And  the sent orders count should be at least 1

  # ---------------------------------------------------------------------------
  # Bulk flow – Burst (continuous)
  # ---------------------------------------------------------------------------

  Scenario: Run a continuous burst bulk flow and stop it
    When I start a burst bulk flow with 5 orders per burst every 500 ms
    Then the bulk flow should be running
    When I wait 2 seconds
    And  I stop the bulk order flow
    Then the bulk flow should not be running

  # ---------------------------------------------------------------------------
  # Bulk flow – Burst (with total order count)
  # ---------------------------------------------------------------------------

  Scenario: Run a burst bulk flow for a fixed number of orders
    When I start a burst bulk flow of 20 total orders with 5 per burst every 500 ms
    Then the bulk flow should be running
    When I wait 3 seconds
    And  I stop the bulk order flow
    Then the bulk flow should not be running
    And  the sent orders count should be at least 1

  # ---------------------------------------------------------------------------
  # FIX tape verification
  # ---------------------------------------------------------------------------

  Scenario: Verify FIX tape captures outbound messages
    When I send a New Order Single for 100 shares of "AAPL"
    Then the FIX tape should contain at least 1 message

  # ---------------------------------------------------------------------------
  # KPI verifications
  # ---------------------------------------------------------------------------

  Scenario: Verify execution report count after sending
    When I send a New Order Single for 200 shares of "MSFT"
    Then the execution report count should be at least 0
    And  the send failure count should be 0
    And  the reject count should be at most 100

  # ---------------------------------------------------------------------------
  # Session verification
  # ---------------------------------------------------------------------------

  Scenario: Verify session connectivity
    Then the session should be connected

  # ---------------------------------------------------------------------------
  # Scenario Outline – Bulk flow with different rates
  # ---------------------------------------------------------------------------

  Scenario Outline: Bulk flow with different fixed rates
    When I start a fixed rate bulk flow of <total> total orders at <rate> orders per second
    Then the bulk flow should be running
    When I stop the bulk order flow
    Then the bulk flow should not be running

    Examples:
      | total | rate |
      | 5     | 2    |
      | 20    | 10   |
      | 50    | 25   |

  # ---------------------------------------------------------------------------
  # Scenario Outline – Send orders for multiple symbols
  # ---------------------------------------------------------------------------

  Scenario Outline: Send limit orders for multiple symbols
    When I send a New Order Single to buy <qty> shares of "<symbol>" at <price>
    Then the sent orders count should be at least 1
    And  the order blotter should contain an order with symbol "<symbol>"

    Examples:
      | symbol | qty | price  |
      | AAPL   | 100 | 170.00 |
      | MSFT   | 200 | 415.00 |
      | NVDA   | 50  | 875.00 |
      | AMZN   | 75  | 200.00 |
      | IBM    | 300 | 145.00 |
      | GS     | 50  | 480.00 |

  # ---------------------------------------------------------------------------
  # Scenario Outline – All supported order types via single NOS
  # ---------------------------------------------------------------------------

  Scenario Outline: Send orders with various order types
    When I send a New Order Single to buy 100 shares of "AAPL" as <orderType> order
    Then the sent orders count should be at least 1

    Examples:
      | orderType       |
      | MARKET          |
      | LIMIT           |
      | MARKET_ON_CLOSE |

  # ---------------------------------------------------------------------------
  # Scenario Outline – Burst flows with different parameters
  # ---------------------------------------------------------------------------

  Scenario Outline: Burst bulk flow with different burst configurations
    When I start a burst bulk flow of <total> total orders with <burst> per burst every <interval> ms
    Then the bulk flow should be running
    When I wait 2 seconds
    And  I stop the bulk order flow
    Then the bulk flow should not be running

    Examples:
      | total | burst | interval |
      | 10    | 2     | 1000     |
      | 20    | 5     | 500      |
      | 30    | 10    | 250      |
