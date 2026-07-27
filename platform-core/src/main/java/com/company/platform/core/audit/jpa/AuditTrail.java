package com.company.platform.core.audit.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/** Reusable JPA mapped superclass for ownership, timestamps, soft deletion, and locking. */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", updatable = false, nullable = false)
    Long id;

    @Column(name = "IS_DELETED", nullable = false)
    Boolean isDeleted = Boolean.FALSE;

    @CreatedBy
    @Column(name = "CREATED_BY", updatable = false)
    String createdBy;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
    OffsetDateTime createdAt;

    @LastModifiedBy
    @Column(name = "UPDATED_BY")
    String updatedBy;

    @LastModifiedDate
    @Column(name = "UPDATED_AT", columnDefinition = "TIMESTAMP(6)")
    OffsetDateTime updatedAt;

    @Version
    @Column(name = "VERSION", nullable = false)
    Long version;
}
