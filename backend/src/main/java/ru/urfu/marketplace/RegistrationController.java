package ru.urfu.marketplace;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    @ExceptionHandler(RegistrationService.InvalidCompanyRole.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidCompanyRole() {
        String message = "Данные компании доступны только заказчику.";
        return new ApiError("VALIDATION_ERROR", message, Instant.now(), List.of(new FieldError("company", message)));
    }

    @ExceptionHandler(RegistrationService.EmailAlreadyRegistered.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError emailTaken() {
        String message = "Этот email уже зарегистрирован.";
        return new ApiError("EMAIL_ALREADY_EXISTS", message, Instant.now(), List.of(new FieldError("email", message)));
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 200) String fullName,
            @Size(max = 32) String phone,
            @NotNull RegistrationRole role,
            @Valid CompanyInput company
    ) {}

    enum RegistrationRole {
        CUSTOMER, EXECUTOR;

        @JsonCreator
        static RegistrationRole fromJson(String value) {
            return valueOf(value);
        }
    }

    record CompanyInput(
            @NotBlank @Size(max = 255) String name,
            @Pattern(regexp = "[0-9]{10}([0-9]{2})?") String inn,
            @URL @Size(max = 255) String website,
            String description
    ) {}

    record AuthResponse(AuthTokens.TokenPair tokens, RegistrationService.User user) {}

    record ApiError(String code, String message, Instant timestamp, List<FieldError> details) {}

    record FieldError(String field, String message) {}
}
