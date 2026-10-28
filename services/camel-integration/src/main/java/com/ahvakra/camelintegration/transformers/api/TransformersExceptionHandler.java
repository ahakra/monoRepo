package com.ahvakra.camelintegration.transformers.api;

import com.ahvakra.camelintegration.fields.domain.FieldsNotFoundException;
import com.ahvakra.camelintegration.transformers.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = TransformerController.class)
public class TransformersExceptionHandler {

    @ExceptionHandler(InvalidTransformerException.class)
    public ProblemDetail invalid(InvalidTransformerException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({ TransformerNotFoundException.class, FieldsNotFoundException.class })
    public ProblemDetail missing(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Check that port names are unique within each direction and their Fields definitions still exist. Refresh and try again."
        );
    }
}
