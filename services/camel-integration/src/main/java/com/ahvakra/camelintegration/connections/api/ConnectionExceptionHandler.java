package com.ahvakra.camelintegration.connections.api;

import com.ahvakra.camelintegration.connections.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = ConnectionController.class)
public class ConnectionExceptionHandler {

    @ExceptionHandler(InvalidConnectionException.class)
    public ProblemDetail invalid(InvalidConnectionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ConnectionNotFoundException.class)
    public ProblemDetail missing(ConnectionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
