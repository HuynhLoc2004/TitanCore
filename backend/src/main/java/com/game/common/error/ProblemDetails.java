package com.game.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail create(HttpStatus status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code);
        problem.setProperty("path", path);
        return problem;
    }

    public static void write(ObjectMapper objectMapper, HttpServletRequest request, HttpServletResponse response,
                             HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), create(status, code, detail, request.getRequestURI()));
    }
}
