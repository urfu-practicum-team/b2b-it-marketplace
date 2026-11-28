package ru.urfu.marketplace.users.profiles.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.urfu.marketplace.users.profiles.entity.CustomerProfile;

import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {}
