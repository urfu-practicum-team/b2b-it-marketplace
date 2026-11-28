package ru.urfu.marketplace.users.profiles.service;

import org.springframework.stereotype.Service;
import ru.urfu.marketplace.users.profiles.dto.CompanyInput;
import ru.urfu.marketplace.users.profiles.entity.CustomerProfile;
import ru.urfu.marketplace.users.profiles.entity.ExecutorProfile;
import ru.urfu.marketplace.users.profiles.repository.CustomerProfileRepository;
import ru.urfu.marketplace.users.profiles.repository.ExecutorProfileRepository;

import java.util.UUID;

@Service
public class ProfileRegistrationService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 120;

    private final CustomerProfileRepository customers;
    private final ExecutorProfileRepository executors;

    ProfileRegistrationService(CustomerProfileRepository customers, ExecutorProfileRepository executors) {
        this.customers = customers;
        this.executors = executors;
    }

    public void createCustomer(UUID userId, CompanyInput company) {
        var profile = company == null
                ? new CustomerProfile(userId, null, null, null, null)
                : new CustomerProfile(userId, company.name(), company.inn(), company.website(), company.description());
        customers.save(profile);
    }

    public void createExecutor(UUID userId, String fullName) {
        int length = fullName.codePointCount(0, fullName.length());
        int end = fullName.offsetByCodePoints(0, Math.min(length, MAX_DISPLAY_NAME_LENGTH));
        executors.save(new ExecutorProfile(userId, fullName.substring(0, end)));
    }
}
