package client.part1;

import client.part2.MetricsCollector;
import client.part2.RequestRecord;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.SkiersApi;
import io.swagger.client.model.LiftRide;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client Part 1 Results:
 * Number of successful requests: 200000
 * Number of failed requests: 0
 * Total wall time: 160769 ms
 * Total throughput: 1244.02 requests/second
 *
 * Client Par 2 Results:
 *
 */
public class MultiThreadClient {
//  private static final int TOTAL_REQUESTS = 200;
//  private static final int INITIAL_THREADS = 10;  // Slight increase
//  private static final int CLEANUP_THREADS = 10;
//  private static final int TOTAL_REQUESTS = 20000;
  //  private static final int INITIAL_THREADS = 25;
//  private static final int CLEANUP_THREADS = 25;
  private static final int INITIAL_THREADS = 32;  // Increase from 24
  private static final int CLEANUP_THREADS = 48;  // Increase from 32
  private static final int TOTAL_REQUESTS = 400000;
  private static final int REQUESTS_PER_THREAD = TOTAL_REQUESTS / INITIAL_THREADS;
//  static final String BASE_PATH = "http://35.160.173.193:8080/distributed-hw1";
  static final String BASE_PATH = "http://tomcat-skier-alb-2082807083.us-west-2.elb.amazonaws.com:8080/distributed-hw1";
  private static final BlockingQueue<LiftRideEvent> eventQueue = new LinkedBlockingQueue<>(10000);
  static final AtomicInteger successfulRequests = new AtomicInteger(0);
  static final AtomicInteger failedRequests = new AtomicInteger(0);
  private static final MetricsCollector metricsCollector = new MetricsCollector();

  public static void main(String[] args) {
    long startTime = System.currentTimeMillis();

    Thread eventGenerator = new Thread(new EventGenerator(eventQueue, TOTAL_REQUESTS));
    eventGenerator.start();

    ExecutorService initialPhase = Executors.newFixedThreadPool(INITIAL_THREADS);
    CountDownLatch initialLatch = new CountDownLatch(INITIAL_THREADS);

    for (int i = 0; i < INITIAL_THREADS; i++) {
      initialPhase.submit(new RequestSender(REQUESTS_PER_THREAD, eventQueue, initialLatch, metricsCollector));
    }

    try {
      initialLatch.await();
      initialPhase.shutdown();

      int remainingRequests = TOTAL_REQUESTS - (INITIAL_THREADS * REQUESTS_PER_THREAD);
      if (remainingRequests > 0) {
        // Calculate how many threads we'll actually need
        int requestsPerCleanupThread = (int) Math.ceil((double) remainingRequests / CLEANUP_THREADS);
        int actualCleanupThreads = Math.min(CLEANUP_THREADS, remainingRequests);

        ExecutorService cleanupPhase = Executors.newFixedThreadPool(actualCleanupThreads);
        CountDownLatch cleanupLatch = new CountDownLatch(actualCleanupThreads);

        int tempRemainingRequests = remainingRequests;
        for (int i = 0; i < actualCleanupThreads; i++) {
          int threadRequests = Math.min(requestsPerCleanupThread, tempRemainingRequests);
          if (threadRequests > 0) {
            cleanupPhase.submit(new RequestSender(threadRequests, eventQueue, cleanupLatch, metricsCollector));
            tempRemainingRequests -= threadRequests;
          }
        }

        cleanupLatch.await();
        cleanupPhase.shutdown();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.out.println("Main thread interrupted: " + e.getMessage());
    }

    System.out.println("All threads finished processing.");

    long wallTime = System.currentTimeMillis() - startTime;

    metricsCollector.writeRecordsToFile("request_records.csv");

    System.out.println("\n=== Client Configuration ===");
    System.out.println("Initial Phase Threads: " + INITIAL_THREADS);
    System.out.println("Cleanup Phase Threads: " + CLEANUP_THREADS);
    System.out.println("Total Requests: " + TOTAL_REQUESTS);

    System.out.println("\n=== Part 1 Results ===");
    System.out.println("Number of successful requests: " + successfulRequests.get());
    System.out.println("Number of failed requests: " + failedRequests.get());
    System.out.println("Wall time: " + wallTime + " ms");
    System.out.println("Throughput: " +
        String.format("%.2f", successfulRequests.get() / (wallTime / 1000.0)) + " requests/second");

    System.out.println("\n=== Part 2 Statistics ===");
    metricsCollector.printStatistics();
  }
}

class LiftRideEvent {
  private final LiftRide liftRide;
  private final int resortId;
  private final int skierId;

  public LiftRideEvent(LiftRide liftRide, int resortId, int skierId) {
    this.liftRide = liftRide;
    this.resortId = resortId;
    this.skierId = skierId;
  }

  public LiftRide getLiftRide() {
    return liftRide;
  }

  public int getResortId() {
    return resortId;
  }

  public int getSkierId() {
    return skierId;
  }
}

class EventGenerator implements Runnable {
  private final BlockingQueue<LiftRideEvent> queue;
  private final int totalEvents;
  private final ThreadLocalRandom random = ThreadLocalRandom.current();

  public EventGenerator(BlockingQueue<LiftRideEvent> queue, int totalEvents) {
    this.queue = queue;
    this.totalEvents = totalEvents;
  }

  public LiftRideEvent generateEvent() {
    LiftRide liftRide = new LiftRide();
    liftRide.setTime(random.nextInt(1, 361));
    liftRide.setLiftID(random.nextInt(1, 41));
    return new LiftRideEvent(liftRide, random.nextInt(1,11), random.nextInt(1,100001));
  }

  @Override
  public void run() {
    try {
      for (int i = 0; i < totalEvents; i++) {
        queue.put(generateEvent());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

class RequestSender implements Runnable {
  private static final AtomicInteger CONCURRENT_REQUESTS = new AtomicInteger(0);
  private static final int MAX_CONCURRENT_REQUESTS = 1000;

  private final int requests;
  private final BlockingQueue<LiftRideEvent> queue;
  private final CountDownLatch latch;
  private final MetricsCollector metricsCollector;

  public RequestSender(int requests, BlockingQueue<LiftRideEvent> queue, CountDownLatch latch, MetricsCollector metricsCollector) {
    this.requests = requests;
    this.queue = queue;
    this.latch = latch;
    this.metricsCollector = metricsCollector;
  }

  public boolean sendRequests(SkiersApi skiersApi, LiftRideEvent liftRideEvent) {
    int currentConcurrent = CONCURRENT_REQUESTS.incrementAndGet();
    int retries = 0;

    try {
      // Implement backpressure if too many concurrent requests
      if (currentConcurrent > MAX_CONCURRENT_REQUESTS) {
        long waitTime = Math.min(100 * (currentConcurrent / MAX_CONCURRENT_REQUESTS), 2000);
        Thread.sleep(waitTime);
      }

      while (retries < 5) {
        long startTime = System.currentTimeMillis();
        try {
          skiersApi.writeNewLiftRide(
              liftRideEvent.getLiftRide(),
              liftRideEvent.getResortId(),
              "2025",
              "1",
              liftRideEvent.getSkierId()
          );
          long endTime = System.currentTimeMillis();
          metricsCollector.addRecord(new RequestRecord(
              startTime,
              "POST",
              endTime - startTime,
              201
          ));

          return true;
        } catch (ApiException e) {
          long endTime = System.currentTimeMillis();
          System.out.println("Request failed with code: " + e.getCode() + ", attempt: " + (retries + 1));

          if (retries == 4) { // Last attempt
            metricsCollector.addRecord(new RequestRecord(
                startTime,
                "POST",
                endTime - startTime,
                e.getCode()
            ));
            return false;
          }

          retries++;
          try {
            Thread.sleep(Math.min(100 * retries, 1000)); // exponential backoff
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
          }
        }
      }
      return false; // This should never be reached if retries < 5
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } finally {
      CONCURRENT_REQUESTS.decrementAndGet();
    }
  }

  @Override
  public void run() {
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(MultiThreadClient.BASE_PATH);
    apiClient.addDefaultHeader("Content-Type", "application/json");
    apiClient.getHttpClient().setConnectTimeout(5000, TimeUnit.MILLISECONDS);
    apiClient.getHttpClient().setReadTimeout(10000, TimeUnit.MILLISECONDS);
    SkiersApi skiersApi = new SkiersApi(apiClient);
    try {
      for (int i = 0; i < requests; i++) {
        LiftRideEvent liftRideEvent = queue.take();
        boolean success = sendRequests(skiersApi, liftRideEvent);
        if (success) {
          MultiThreadClient.successfulRequests.incrementAndGet();
        } else {
          MultiThreadClient.failedRequests.incrementAndGet();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      latch.countDown();
    }
  }
}