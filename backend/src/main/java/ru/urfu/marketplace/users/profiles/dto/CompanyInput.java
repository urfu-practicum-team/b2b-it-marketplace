package ru.urfu.marketplace.users.profiles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CompanyInput(
        @NotBlank @Size(max = 255) String name,
        @Pattern(regexp = "[0-9]{10}([0-9]{2})?") String inn,
        @URL @Size(max = 255) String website,
        String description
) {}
