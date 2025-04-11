# SkiTrack: Ski Resort RFID Data Processing System

# Distributed Ski Resort System

A high-performance distributed system for processing ski lift ride data

## Overview

This system processes ski lift ride data through a distributed architecture designed for scalability and fault tolerance:

- **Java Servlet API**: Validates requests and publishes to message queue
- **RabbitMQ Message Broker**: Buffers messages between components
- **Multi-threaded Consumer**: Processes messages and maintains skier records

## Key Components

### Server (SkierServlet)
- RESTful API following the required URL pattern
- Thorough request validation
- Connection pooling with RabbitMQ (50 channels)
- Persistent message publishing

### Message Queue
- Durable queue configuration
- Message persistence for reliability
- Deployed on dedicated EC2 instance

### Consumer
- 40 concurrent worker threads
- Prefetch count of 20 for optimal throughput
- Thread-safe data structures (ConcurrentHashMap, CopyOnWriteArrayList)
- Explicit message acknowledgment

## Performance Highlights

- **Optimized Configuration**:
  - Client: 32 initial threads, 48 cleanup threads
  - Connection timeouts: 5s connect, 10s read
  - Consumer: 40 threads with prefetch count of 20

- **Load Balanced Performance**:
  - Throughput: ~1,260 messages/second
  - 400,000 messages processed in 317.45 seconds
  - Stable queue depth (no message backlog)

## Quick Start

1. **Deploy RabbitMQ**:
   ```bash
   sudo apt-get update
   sudo apt-get install rabbitmq-server
   sudo rabbitmqctl add_user admin password
   sudo rabbitmqctl set_user_tags admin administrator
   ```

2. **Deploy Servlet**:
   - Build WAR file with Maven
   - Deploy to Tomcat on EC2 instances
   - Configure behind AWS ALB

3. **Run Consumer**:
   ```bash
   java -jar consumer.jar
   ```

## Monitoring

Access the RabbitMQ Management Console:
```
http://[rabbitmq-ip]:15672/
```
