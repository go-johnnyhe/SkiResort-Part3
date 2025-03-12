import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import java.util.concurrent.TimeoutException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/skiers/*")
public class SkierServlet extends HttpServlet {
    private final Gson gson = new Gson();
    private RabbitMQConnectionPool connectionPool;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            connectionPool = new RabbitMQConnectionPool("34.217.15.111");
        } catch (IOException | TimeoutException e) {
            throw new ServletException("Failed to initialize RabbitMQ connection pool", e);
        }
    }

    @Override
    public void destroy() {
        if (connectionPool != null) {
            connectionPool.close();
        }
        super.destroy();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json");
        Channel channel = null;


        PrintWriter out = res.getWriter();
        try {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null) {
                sendError(res, HttpServletResponse.SC_BAD_REQUEST, "invalid url");
                return;
            }

            String[] parts = pathInfo.split("/");
            if (parts.length != 8 || !"seasons".equals(parts[2]) || !"days".equals(parts[4]) || !"skiers".equals(parts[6])) {
                sendError(res, HttpServletResponse.SC_BAD_REQUEST, "invalid url structure");
                return;
            }

            String resortIdStr = parts[1];
            String seasonId = parts[3];
            String daysId = parts[5];
            String skiersIdStr = parts[7];

            //validate IDs
            if (validateResortID(resortIdStr, res) == -1) {
                return;
            }

            if (!"2025".equals(seasonId)) {
                sendError(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid season ID");
                return;
            }

            if (!"1".equals(daysId)) {
                sendError(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid day ID");
                return;
            }

            if (validateSkierId(skiersIdStr, res) == -1) {
                return;
            }

            String contentType = req.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
                sendError(res, HttpServletResponse.SC_BAD_REQUEST, "Content type must be application/json");
                return;
            }

            LiftRide liftRide = parseLiftRide(req, res);
            if (liftRide == null) {
                return;
            }

            if (liftRide.getTime() < 1 || liftRide.getTime() > 360) {
                sendError(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid time value");
                return;
            }

            if (liftRide.getLiftID() < 1 || liftRide.getLiftID() > 40) {
                sendError(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid lift ID");
                return;
            }

            SkierRecord record = new SkierRecord(
                Integer.parseInt(parts[1]),
                parts[3],
                parts[5],
                Integer.parseInt(parts[7]),
                liftRide.getTime(),
                liftRide.getLiftID()
            );

            channel = connectionPool.getChannel();
            String message = gson.toJson(record);
            System.out.println("About to publish message to RabbitMQ: " + message);
            channel.basicPublish("", connectionPool.getQueueName(), MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes());
            System.out.println("Message successfully published to RabbitMQ!");

            res.setStatus(HttpServletResponse.SC_CREATED);
        } catch (InterruptedException e) {
            sendError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to acquire channel");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            sendError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
            e.printStackTrace();
        } finally {
            if (channel != null) {
                try {
                    connectionPool.returnChannel(channel);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            out.close();
        }
    }

    private int validateResortID(String resortIdStr, HttpServletResponse res) throws IOException {
        try {
            int resortId = Integer.parseInt(resortIdStr);
            if (resortId < 1 || resortId > 10) {
                sendError(res, HttpServletResponse.SC_BAD_REQUEST, "Resort ID must be between 1 and 10");
                return -1;
            }
            return resortId;
        } catch (NumberFormatException e) {
            sendError(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid resort ID format");
            return -1;
        }
    }

    private int validateSkierId(String skierIdStr, HttpServletResponse response) throws IOException {
        try {
            int skierId = Integer.parseInt(skierIdStr);
            if (skierId < 1 || skierId > 100000) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Skier ID must be between 1 and 100000");
                return -1;
            }
            return skierId;
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid skier ID format");
            return -1;
        }
    }

    private LiftRide parseLiftRide(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (BufferedReader reader = request.getReader()) {
            return gson.fromJson(reader, LiftRide.class);
        } catch (JsonSyntaxException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format");
            return null;
        }
    }

    private void sendError(HttpServletResponse res, int statusCode, String message) throws IOException {
        res.setStatus(statusCode);
        res.getWriter().print("{\"message\": \"" + message + "\"}");
    }
}



class LiftRide {
    private Integer time;
    private Integer liftID;

    public Integer getTime() { return time; }
    public void setTime(Integer time) { this.time = time; }
    public Integer getLiftID() { return liftID; }
    public void setLiftID(Integer liftID) { this.liftID = liftID; }
}

class SkierRecord {
    private final int resortId;
    private final String seasonId;
    private final String dayId;
    private final int skierId;
    private final int time;
    private final int liftId;
    public SkierRecord(int resortId, String seasonId, String dayId,
        int skierId, int time, int liftId) {
        this.resortId = resortId;
        this.seasonId = seasonId;
        this.dayId = dayId;
        this.skierId = skierId;
        this.time = time;
        this.liftId = liftId;
    }

    public int getResortId() {
        return resortId;
    }

    public String getSeasonId() {
        return seasonId;
    }

    public String getDayId() {
        return dayId;
    }

    public int getSkierId() {
        return skierId;
    }

    public int getTime() {
        return time;
    }

    public int getLiftId() {
        return liftId;
    }
}