import java.util.List;
import java.util.Map;
import java.util.Set;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

public class RedisDAO {
  private final JedisPool jedisPool;

  // Add Redis password
  public RedisDAO(String host, int port) {
    this(host, port, "password");
  }

  // Constructor with password
  public RedisDAO(String host, int port, String password) {
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(300);
    config.setMaxIdle(80);
    config.setMinIdle(40);
    config.setBlockWhenExhausted(true);
    config.setTestOnBorrow(true);

    // Create pool with password if provided
    if (password != null && !password.isEmpty()) {
      jedisPool = new JedisPool(config, host, port, 10000, password);
    } else {
      jedisPool = new JedisPool(config, host, port);
    }
  }

  public void updateSkierRecord(List<SkierRecord> records) {
    try (Jedis jedis = jedisPool.getResource()) {
      Pipeline pipeline = jedis.pipelined();

      for (SkierRecord record : records) {
        pipeline.sadd("skier:" + record.getSkierId() + ":days", record.getDayId());
        pipeline.hincrBy("skier:" + record.getSkierId() + ":verticals", record.getDayId(), record.getLiftId() * 10L);
        pipeline.sadd("skier:" + record.getSkierId() + ":lifts:" + record.getDayId(), String.valueOf(record.getLiftId()));
        pipeline.sadd("resort:" + record.getResortId() + ":day:" + record.getDayId() + ":skiers", String.valueOf(record.getSkierId()));
      }

      pipeline.sync();
    }
  }

  public long getSkierDaysCount(int skierId) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.scard("skier:" + skierId + ":days");
    }
  }

  public Map<String, String> getSkierVerticalTotals(int skierId) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.hgetAll("skier:" + skierId + ":verticals");
    }
  }

  public Set<String> getSkierLiftsForDay(int skierId, String dayId) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.smembers("skier:" + skierId + ":lifts:" + dayId);
    }
  }

  public long getUniqueSkiersForResortDay(int resortId, String dayId) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.scard("resort:" + resortId + ":day:" + dayId + ":skiers");
    }
  }

  public void close() {
    jedisPool.close();
  }
}
