package com.company.platform.database;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface DatabaseProbeRepository extends JpaRepository<DatabaseProbeEntity, Long> {

    Optional<DatabaseProbeEntity> findByValue(String value);
}
