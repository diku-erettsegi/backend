package hu.diku.api;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="courses")
class Course {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,length=80,unique=true) String slug;
    @Column(nullable=false,length=120) String name;
    // Wall-clock course values must bypass java.sql.Time + the UTC calendar used for Instant fields.
    @JdbcTypeCode(SqlTypes.LOCAL_DATE) @Column(nullable=false) LocalDate startDate;
    @JdbcTypeCode(SqlTypes.LOCAL_TIME) @Column(nullable=false) LocalTime startTime;
    @JdbcTypeCode(SqlTypes.LOCAL_TIME) @Column(nullable=false) LocalTime endTime;
    @Column(nullable=false) int sessions;
    @Column(nullable=false) int lessonsPerSession;
    @Column(nullable=false) int capacity;
    @Column(nullable=false) boolean registrationOpen;
    @Column(nullable=false) int baseTotal;
    Long getId() { return id; }
    String getName() { return name; }
}

@Entity @Table(name="programming_interests")
class ProgrammingInterest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,length=120,updatable=false) String name;
    @Column(nullable=false,length=254,updatable=false) String email;
    @Column(nullable=false,length=36,unique=true,updatable=false) String requestId;
    @Column(nullable=false,length=64,updatable=false) String requestHash;
    @Column(nullable=false,updatable=false) Instant privacyAcceptedAt;
    @Column(nullable=false,length=40,updatable=false) String privacyVersion;
    @Column(nullable=false,updatable=false) Instant createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) InterestStatus status=InterestStatus.NEW;
    @Column(columnDefinition="TEXT") String notes;
    @Column(nullable=false) Instant updatedAt;
    Long getId() { return id; }
    String getEmail() { return email; }
    String getName() { return name; }
    Instant getCreatedAt() { return createdAt; }
}

enum InterestStatus { NEW, CONTACTED, COMPLETED }
