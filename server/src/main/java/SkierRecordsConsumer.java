import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class SkierRecordsConsumer {
  private static final String QUEUE_NAME = "skier_records";
  private static final int NUM_THREADS = 40;
  private final String host;
  private final ConcurrentHashMap<Integer, List<SkierRecord>> skierRecords;
  private final AtomicInteger processedMessages;
  private final ExecutorService executorService;
  private final Connection connection;
  private final Gson gson;

  public SkierRecordsConsumer(String host) throws IOException, TimeoutException {
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
  }

  public void startConsuming() throws IOException {
    for (int i = 0; i < NUM_THREADS; i++) {
      executorService.submit(() -> {
        try {
          Channel channel = connection.createChannel();
          channel.queueDeclare(QUEUE_NAME, true, false, false, null);
          channel.basicQos(20);
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
    System.out.println("Consumer received message: " + message);
    SkierRecord record = gson.fromJson(message, SkierRecord.class);
    skierRecords.computeIfAbsent(record.getSkierId(), k -> new CopyOnWriteArrayList<>()).add(record);
  }

  public void shutdown() {
    try {
      executorService.shutdown();
      if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
      connection.close();
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
      SkierRecordsConsumer consumer = new SkierRecordsConsumer("34.217.15.111");
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
