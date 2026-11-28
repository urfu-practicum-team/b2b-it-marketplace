package ru.urfu.marketplace.users.identity.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.urfu.marketplace.common.error.ApiError;
import ru.urfu.marketplace.users.identity.dto.AuthResponse;
import ru.urfu.marketplace.common.error.FieldError;
import ru.urfu.marketplace.users.identity.dto.RegisterRequest;
import ru.urfu.marketplace.users.identity.service.EmailAlreadyRegistered;
import ru.urfu.marketplace.users.identity.service.InvalidCompanyRole;
import ru.urfu.marketplace.users.identity.service.RegistrationService;

import java.time.Instant;
import java.util.List;

@RestController
public class RegistrationController {

    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/api/v1/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@RequestBody @Valid RegisterRequest request) {
        return registration.register(request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidFields(MethodArgumentNotValidException error) {
        List<FieldError> fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new FieldError(field.getField(), field.getDefaultMessage()))
                .toList();
        return new ApiError("VALIDATION_ERROR", "Проверьте поля регистрации.", Instant.now(), fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidRequest() {
        return new ApiError("VALIDATION_ERROR", "Некорректные данные регистрации.", Instant.now(), List.of());
    }

    @ExceptionHandler(InvalidCompanyRole.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidCompanyRole() {
        String message = "Данные компании доступны только заказчику.";
        return new ApiError("VALIDATION_ERROR", message, Instant.now(), List.of(new FieldError("company", message)));
    }

    @ExceptionHandler(EmailAlreadyRegistered.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError emailTaken() {
        String message = "Этот email уже зарегистрирован.";
        return new ApiError("ALREADY_EXISTS", message, Instant.now(), List.of(new FieldError("email", message)));
    }
}
