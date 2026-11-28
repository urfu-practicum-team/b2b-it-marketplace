package ru.urfu.marketplace.users.profiles.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.urfu.marketplace.users.profiles.entity.ExecutorProfile;

import java.util.UUID;

public interface ExecutorProfileRepository extends JpaRepository<ExecutorProfile, UUID> {}
