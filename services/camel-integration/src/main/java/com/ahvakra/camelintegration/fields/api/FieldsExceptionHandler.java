package com.ahvakra.camelintegration.fields.api;

import com.ahvakra.camelintegration.fields.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = FieldDefinitionController.class)
public class FieldsExceptionHandler {

    @ExceptionHandler(FieldsInUseException.class)
    public ProblemDetail used(FieldsInUseException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail inUse() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "This Fields definition is used by a table or rule. Edit or delete the dependent configurations first."
        );
    }

    @ExceptionHandler(InvalidFieldsException.class)
    public ProblemDetail invalid(InvalidFieldsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(FieldsNotFoundException.class)
    public ProblemDetail missing(FieldsNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
