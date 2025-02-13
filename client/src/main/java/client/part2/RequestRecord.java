package client.part2;

public class RequestRecord {
  private final long startTime;
  private final String requestType;
  private final long latency;
  private final int responseCode;

  public RequestRecord(long startTime, String requestType, long latency, int responseCode) {
    this.startTime = startTime;
    this.requestType = requestType;
    this.latency = latency;
    this.responseCode = responseCode;
  }

  public String toCsvString() {
    return String.format("%d,POST,%d,%d%n", startTime, latency, responseCode);
  }

  public long getLatency() {
    return latency;
  }

}

