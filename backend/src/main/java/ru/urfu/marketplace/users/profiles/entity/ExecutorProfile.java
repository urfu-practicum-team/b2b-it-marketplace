package ru.urfu.marketplace.users.profiles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "executor_profiles")
public class ExecutorProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    protected ExecutorProfile() {}

    public ExecutorProfile(UUID userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
    }
}
