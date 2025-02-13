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
  private static final int INITIAL_THREADS = 32;
  private static final int CLEANUP_THREADS = 80;
  private static final int REQUESTS_PER_THREAD = 1000;
  private static final int TOTAL_REQUESTS = 200000;
  static final String BASE_PATH = "http://52.13.106.210:8080/distributed-hw1";
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
        ExecutorService cleanupPhase = Executors.newFixedThreadPool(CLEANUP_THREADS);
        int requestsPerThread = (int) Math.ceil((double) remainingRequests / CLEANUP_THREADS);

        CountDownLatch cleanupLatch = new CountDownLatch(CLEANUP_THREADS);
        for (int i = 0; i < CLEANUP_THREADS; i++) {
          int threadRequests = Math.min(requestsPerThread, remainingRequests);
          if (threadRequests > 0) {
            cleanupPhase.submit(new RequestSender(threadRequests, eventQueue, cleanupLatch, metricsCollector));
            remainingRequests -= threadRequests;
          }
        }

        cleanupLatch.await();
        cleanupPhase.shutdown();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }


    System.out.println("All threads finished processing.");

    long wallTime = System.currentTimeMillis() - startTime;

    metricsCollector.writeRecordsToFile("request_records.csv");

    System.out.println("\n=== Client Configuration ===");
    System.out.println("Initial Phase Threads: " + INITIAL_THREADS);
    System.out.println("Cleanup Phase Threads: " + CLEANUP_THREADS);
    System.out.println("Total Threads Used: " + (INITIAL_THREADS + CLEANUP_THREADS));

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
    int retries = 0;
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
        retries++;
        if (retries == 5) {
          metricsCollector.addRecord(new RequestRecord(
              startTime,
              "POST",
              endTime - startTime,
              e.getCode()
          ));
        }
        if (retries < 5) {
          try {
            Thread.sleep(Math.min(100 * (retries + 1), 700)); // exponential backoff
          } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
            return false;
          }
        }
      }
    }
    return false;
  }

  @Override
  public void run() {
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(MultiThreadClient.BASE_PATH);
    apiClient.addDefaultHeader("Content-Type", "application/json");
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