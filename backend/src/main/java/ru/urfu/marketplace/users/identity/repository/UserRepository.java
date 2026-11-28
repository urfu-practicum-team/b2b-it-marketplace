package ru.urfu.marketplace.users.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.urfu.marketplace.users.identity.entity.UserAccount;

import java.util.UUID;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {}
