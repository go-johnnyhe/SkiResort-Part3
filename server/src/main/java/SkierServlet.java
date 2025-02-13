import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import javax.servlet.annotation.WebServlet;

//package client;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/skiers/*")
public class SkierServlet extends HttpServlet {
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("application/json");
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

            res.setStatus(HttpServletResponse.SC_CREATED);
        } catch (Exception e) {
            sendError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
            e.printStackTrace();
        } finally {
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