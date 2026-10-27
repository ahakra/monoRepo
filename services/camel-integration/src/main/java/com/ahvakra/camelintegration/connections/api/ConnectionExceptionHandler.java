package com.ahvakra.camelintegration.connections.api;

import com.ahvakra.camelintegration.connections.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = ConnectionController.class)
public class ConnectionExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail inUse() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "This connection is used by a table configuration. Delete the dependent table configurations first."
        );
    }

    @ExceptionHandler(InvalidConnectionException.class)
    public ProblemDetail invalid(InvalidConnectionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ConnectionNotFoundException.class)
    public ProblemDetail missing(ConnectionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
