package ru.urfu.marketplace.users.profiles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "company_inn", length = 12)
    private String companyInn;

    @Column(name = "company_website", length = 255)
    private String companyWebsite;

    @Column(name = "company_description", columnDefinition = "text")
    private String companyDescription;

    protected CustomerProfile() {}

    public CustomerProfile(UUID userId, String companyName, String companyInn,
                           String companyWebsite, String companyDescription) {
        this.userId = userId;
        this.companyName = companyName;
        this.companyInn = companyInn;
        this.companyWebsite = companyWebsite;
        this.companyDescription = companyDescription;
    }
}
