package com.ahvakra.camelintegration.tables.api;

import com.ahvakra.camelintegration.tables.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = TableDefinitionController.class)
public class TablesExceptionHandler {

    @ExceptionHandler(TableSchemaException.class)
    public ProblemDetail schema(TableSchemaException exception) {
        return ProblemDetail.forStatusAndDetail(
            exception.conflict() ? HttpStatus.CONFLICT : HttpStatus.BAD_GATEWAY,
            exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidTableException.class)
    public ProblemDetail invalid(InvalidTableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(TableNotFoundException.class)
    public ProblemDetail missing(TableNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(TableCreationException.class)
    public ProblemDetail creation(TableCreationException exception) {
        return ProblemDetail.forStatusAndDetail(
            exception.conflict() ? HttpStatus.CONFLICT : HttpStatus.BAD_GATEWAY,
            exception.getMessage()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "This connection and table name are already configured, or a selected connection or Fields definition was removed. Refresh and check your selection."
        );
    }
}
