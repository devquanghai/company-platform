package com.company.platform.database;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "database_probe")
class DatabaseProbeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String value;

    protected DatabaseProbeEntity() {
    }

    DatabaseProbeEntity(String value) {
        this.value = value;
    }

    Long getId() {
        return id;
    }

    String getValue() {
        return value;
    }
}
