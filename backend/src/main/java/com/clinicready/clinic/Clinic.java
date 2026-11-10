package com.clinicready.clinic;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The tenant. Every other domain row belongs to exactly one clinic. */
@Entity
@Table(name = "clinic")
public class Clinic {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String city;

    protected Clinic() {
    }

    public Clinic(String name, String city) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.city = city;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }
}
