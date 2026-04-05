package com.dentalManagement.dentalFlowBackend.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

public class FilterErrorResponseUtil {

    // Private constructor — no one should instantiate this class
    private FilterErrorResponseUtil() {}

    public static void sendErrorResponse(HttpServletResponse response,
                                         int status,
                                         String error,
                                         String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                "{" +
                        "\"timestamp\": \"" + LocalDateTime.now() + "\"," +
                        "\"status\": " + status + "," +
                        "\"error\": \"" + error + "\"," +
                        "\"message\": \"" + message + "\"" +
                        "}"
        );
    }
}
