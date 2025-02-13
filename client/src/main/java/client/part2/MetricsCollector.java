package client.part2;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MetricsCollector {
  private final ConcurrentLinkedQueue<RequestRecord> records = new ConcurrentLinkedQueue<>();

  public void addRecord(RequestRecord record) {
    records.add(record);
  }

  public void writeRecordsToFile(String fileName) {
    try (FileWriter writer = new FileWriter(fileName)) {
      writer.write("StartTime,RequestType,Latency,ResponseCode\n");
      for (RequestRecord record : records) {
        writer.write(record.toCsvString());
      }
    } catch (IOException e) {
      System.err.println("Error writing to file: " + fileName + ", " + e.getMessage());
    }
  }

  public void printStatistics() {
    List<Long> latencies = new ArrayList<>();
    for (RequestRecord record : records) {
      latencies.add(record.getLatency());
    }
    Collections.sort(latencies);

    double mean = calculateMean(latencies);
    long median = calculateMedian(latencies);
    long p99 = calculatePercentile(latencies, 0.99);
    long min = latencies.get(0);
    long max = latencies.get(latencies.size() - 1);

    // Print results
    System.out.println("\nRequest Latency Statistics:");
    System.out.println("Mean response time: " + String.format("%.2f", mean) + " ms");
    System.out.println("Median response time: " + median + " ms");
    System.out.println("p99 response time: " + p99 + " ms");
    System.out.println("Min response time: " + min + " ms");
    System.out.println("Max response time: " + max + " ms");

  }

  private double calculateMean(List<Long> values) {
    return values.stream()
        .mapToLong(Long::longValue)
        .average()
        .orElse(0.0);
  }

  private long calculateMedian(List<Long> values) {
    int middle = values.size() / 2;
    if (values.size() % 2 == 1) {
      return values.get(middle);
    } else {
      return (values.get(middle - 1) + values.get(middle)) / 2;
    }
  }

  private long calculatePercentile(List<Long> values, double percentile) {
    int index = (int) Math.ceil(percentile * values.size()) - 1;
    return values.get(index);
  }
}
