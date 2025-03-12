import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

public class RabbitMQConnectionPool {
  public static final int POOL_SIZE = 50;
  private final BlockingQueue<Channel> channelPool;
  private final Connection connection;
  private final String QUEUE_NAME = "skier_records";

  public RabbitMQConnectionPool(String host) throws IOException, TimeoutException {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setUsername("admin");
    factory.setPassword("password");

    connection = factory.newConnection();
    channelPool = new LinkedBlockingQueue<>(POOL_SIZE);

    for (int i = 0; i < POOL_SIZE; i++) {
      Channel channel = connection.createChannel();
      channel.queueDeclare(QUEUE_NAME, true, false, false, null);
      if (!channelPool.offer(channel)) {
        try {
          channel.close();
        } catch (TimeoutException e) {
          e.printStackTrace();
        }
        throw new IOException("Failed to initialize channel pool - pool is full");
      }
    }
  }

  public Channel getChannel() throws InterruptedException {
    return channelPool.take();
  }

  public void returnChannel(Channel channel) throws IOException {
    if (channel != null) {
      if (!channelPool.offer(channel)) {
        try {
          channel.close();
        }
        catch (IOException | TimeoutException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void close() {
    try {
      for (Channel channel : channelPool) {
        if (channel != null && channel.isOpen()) {
          channel.close();
        }
      }
      if (connection != null && connection.isOpen()) {
        connection.close();
      }
    } catch (IOException | TimeoutException e) {
      e.printStackTrace();
    }
  }

  public String getQueueName() {
    return QUEUE_NAME;
  }
}
