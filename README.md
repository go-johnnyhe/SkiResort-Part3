# CS6650 Assignment 1 - Client Implementation

## Overview

This client implements a multi-threaded solution for sending 200,000 POST requests to a ski resort RFID system, following the requirements of CS6650 Assignment 1. The client includes metrics collection and performance statistics.

## Setup Instructions

1. Clone this repository
2. Open the project in IntelliJ IDEA
3. Ensure you have Java 11+ installed
4. The main method is in **MultiThreadClient** class - simply run this file in IntelliJ

## Configuration

- **Server URL**: Located in the **MultiThreadClient** class. Modify the `BASE_PATH` constant at the top of the file
- **Thread Configuration**:
  - Initial phase: 32 threads (as per assignment requirements)
  - Cleanup phase: 80 threads
  - Each initial thread handles 1000 requests
- **Queue Size**: The blocking queue for lift ride events has a capacity of 10000

## Dependencies

- Swagger Client API
- Java 11+
- Maven

## Implementation Notes

- Uses a dedicated thread for event generation
- Implements retry logic (up to 5 attempts) for failed requests
- Collects comprehensive metrics including mean, median, and p99 latencies
- Records request data in CSV format for analysis

### Expected output

```java
All threads finished processing.

=== Client Configuration ===
Initial Phase Threads: 32
Cleanup Phase Threads: 80
Total Threads Used: 112
=== Part 1 Results ===
Number of successful requests: 200000
Number of failed requests: 0
Wall time: 91193 ms
Throughput: 2193.15 requests/second
=== Part 2 Statistics ===

Request Latency Statistics:

Mean response time: 29.62 ms

Median response time: 27 ms
p99 response time: 80 ms

Min response time: 13 ms

Max response time: 538 ms
```
