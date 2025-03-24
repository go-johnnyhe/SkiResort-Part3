import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SkierRecordsConsumer {
  private static final String QUEUE_NAME = "skier_records";
  private static final int NUM_THREADS = 60;
  private final String host;
  private final ConcurrentHashMap<Integer, List<SkierRecord>> skierRecords;
  private final AtomicInteger processedMessages;
  private final ExecutorService executorService;
  private final Connection connection;
  private final Gson gson;
  private final RedisDAO redisDao;

  private final int BATCH_SIZE = 100;
  private final List<SkierRecord> recordBatch = new ArrayList<>(BATCH_SIZE);
  private final Object batchLock = new Object();

  private final AtomicInteger messagesPerSecond = new AtomicInteger(0);
  private final AtomicInteger redisOpsPerSecond = new AtomicInteger(0);
  private final AtomicLong totalProcessingTime = new AtomicLong(0);

  public SkierRecordsConsumer(String host, String redisHost, int redisPort, String redisPassword) throws IOException, TimeoutException {
    this.host = host;
    this.skierRecords = new ConcurrentHashMap<>();
    this.processedMessages = new AtomicInteger(0);
    this.executorService = Executors.newFixedThreadPool(NUM_THREADS);
    this.gson = new Gson();

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setUsername("admin");
    factory.setPassword("password");
    factory.setRequestedHeartbeat(30);
    factory.setAutomaticRecoveryEnabled(true);


    this.connection = factory.newConnection();

    this.redisDao = new RedisDAO(redisHost, redisPort, redisPassword);
  }

  private void startMonitoring() {
    new Thread(() -> {
      try {
        while (true) {
          int messages = messagesPerSecond.getAndSet(0);
          int redisOps = redisOpsPerSecond.getAndSet(0);
          long avgProcessingTime = totalProcessingTime.get() / (messages > 0 ? messages : 1);
          totalProcessingTime.set(0);

          System.out.println(String.format("[MONITOR] Messages/sec: %d, Redis ops/sec: %d, Avg processing time: %d ms",
              messages, redisOps, avgProcessingTime));
          Thread.sleep(1000);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }).start();
  }

  public void startConsuming() throws IOException {
    startMonitoring();
    for (int i = 0; i < NUM_THREADS; i++) {
      executorService.submit(() -> {
        try {
          Channel channel = connection.createChannel();
          channel.queueDeclare(QUEUE_NAME, true, false, false, null);
          channel.basicQos(50);
          DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
              String message = new String(delivery.getBody(), "UTF-8");
              processMessage(message);

              channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

              int processed = processedMessages.incrementAndGet();
              if (processed % 10000 == 0) {
                System.out.println(processed + " messages processed");
              }
            } catch (Exception e) {
              System.err.println("Error processing " + e.getMessage());
              channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
          };
          channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });
        } catch (IOException e) {
          System.err.println("Error consuming " + e.getMessage());
        }
      });
    }
  }

  private void processMessage(String message) {

    long startTime = System.currentTimeMillis();

    SkierRecord record = gson.fromJson(message, SkierRecord.class);
    skierRecords.computeIfAbsent(record.getSkierId(), k -> new CopyOnWriteArrayList<>()).add(record);

    synchronized(batchLock) {
      recordBatch.add(record);
      if (recordBatch.size() >= BATCH_SIZE) {
        processBatch(new ArrayList<>(recordBatch));
        recordBatch.clear();
      }
    }

    messagesPerSecond.incrementAndGet();
    long processingTime = System.currentTimeMillis() - startTime;
    totalProcessingTime.addAndGet(processingTime);
  }


  private void processBatch(List<SkierRecord> batch) {
    try {
      redisDao.updateSkierRecord(batch);
      redisOpsPerSecond.addAndGet(batch.size());
    } catch (Exception e) {
      System.err.println("Error processing batch: " + e.getMessage());
      // Consider individual processing as fallback
      for (SkierRecord record : batch) {
        try {
          List<SkierRecord> singleRecord = new ArrayList<>();
          singleRecord.add(record);
          redisDao.updateSkierRecord(singleRecord);
          redisOpsPerSecond.incrementAndGet();
        } catch (Exception innerEx) {
          System.err.println("Failed to process record: " + record.getSkierId() + " - " + innerEx.getMessage());
        }
      }
    }
  }

  public void shutdown() {
    try {
      synchronized(batchLock) {
        if (!recordBatch.isEmpty()) {
          processBatch(new ArrayList<>(recordBatch));
          recordBatch.clear();
        }
      }
      executorService.shutdown();
      if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
      connection.close();
      redisDao.close();
    } catch (Exception e) {
      System.err.println("Error during shutdown: " + e.getMessage());
    }
  }

  public Map<Integer, List<SkierRecord>> getSkierRecords() {
    return skierRecords;
  }

  public int getProcessedMessageCount() {
    return processedMessages.get();
  }

  public static void main(String[] args) {
    try {
      String redisPassword = "password";
      RedisDAO testRedis = new RedisDAO("52.39.204.100", 6379, redisPassword);
      try {
        System.out.println("Testing Redis connection...");
        List<SkierRecord> testList = new ArrayList<>();
        testList.add(new SkierRecord(1, "2025", "1", 1, 100, 10));
        testRedis.updateSkierRecord(testList);
        System.out.println("Redis connection test successful!");
        testRedis.close();
      } catch (Exception e) {
        System.err.println("Redis connection test failed: " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
      }
//      String redisPassword = "password";
      SkierRecordsConsumer consumer = new SkierRecordsConsumer("44.226.51.114", "52.39.204.100", 6379, redisPassword);
      consumer.startConsuming();

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("Shutting down consumer...");
        consumer.shutdown();
        System.out.println("Processed total of " + consumer.getProcessedMessageCount() + " messages");
        System.out.println("Total unique skiers: " + consumer.getSkierRecords().size());
      }));

      System.out.println("Consumer started. Press Ctrl+C to exit.");
      Thread.currentThread().join();
    } catch (Exception e) {
      System.err.println("Error starting consumer: " + e.getMessage());
      System.exit(1);
    }
  }

}
