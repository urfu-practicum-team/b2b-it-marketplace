package ru.urfu.marketplace.users.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.urfu.marketplace.users.profiles.dto.CompanyInput;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 32) String phone,
        @NotNull RegistrationRole role,
        @Valid CompanyInput company
) {}
