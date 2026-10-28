package com.ahvakra.camelintegration.rules.api;

import com.ahvakra.camelintegration.fields.domain.FieldsNotFoundException;
import com.ahvakra.camelintegration.rules.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(
    assignableTypes = { NormalizeRuleController.class, ConditionalRuleController.class }
)
public class RulesExceptionHandler {

    @ExceptionHandler(InvalidRuleException.class)
    public ProblemDetail invalid(InvalidRuleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({
        RuleNotFoundException.class,
        ConditionalRuleNotFoundException.class,
        FieldsNotFoundException.class,
    })
    public ProblemDetail missing(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "The selected Fields definition changed. Refresh and check your selection."
        );
    }
}
