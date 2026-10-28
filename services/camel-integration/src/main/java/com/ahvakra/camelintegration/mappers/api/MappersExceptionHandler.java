package com.ahvakra.camelintegration.mappers.api;

import com.ahvakra.camelintegration.fields.domain.FieldsNotFoundException;
import com.ahvakra.camelintegration.mappers.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = MapperController.class)
public class MappersExceptionHandler {

    @ExceptionHandler(InvalidMapperException.class)
    public ProblemDetail invalid(InvalidMapperException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({ MapperNotFoundException.class, FieldsNotFoundException.class })
    public ProblemDetail missing(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Check that output names are unique and their Fields definitions still exist. Refresh and try again."
        );
    }
}
