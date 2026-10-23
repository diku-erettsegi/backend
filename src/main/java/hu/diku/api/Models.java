package hu.diku.api;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

enum Role { ADMIN, STUDENT }
enum ApplicationStatus { NEW, CONTACTED, ACCEPTED, REJECTED }

@Entity @Table(name = "users")
class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 120) String name;
    @Column(nullable = false, length = 254, unique = true) String email;
    @Column(name = "password_hash", nullable = false, length = 100) String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) Role role;
    @Column(nullable = false) boolean enabled = true;
    @Column(nullable = false) long authVersion = 0;
    @Column(nullable = false) Instant createdAt = Instant.now();
}

@Entity @Table(name = "applications")
class Application {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 120) String studentName;
    @Column(nullable = false, length = 254) String email;
    @Column(nullable = false, length = 40) String phone;
    @Column(length = 120) String parentName;
    @Column(columnDefinition = "TEXT") String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) ApplicationStatus status = ApplicationStatus.NEW;
    @Column(columnDefinition = "TEXT") String notes;
    @Column(nullable = false) Instant privacyAcceptedAt = Instant.now();
    @Column(nullable = false, length = 40) String privacyVersion = "draft-2026-10";
    @Column(nullable = false, updatable = false) Instant createdAt = Instant.now();
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="course_id",updatable=false) Course course;
    @Column(length=36,unique=true,updatable=false) String requestId;
    @Column(length=64,updatable=false) String requestHash;
    @Column(nullable=false,updatable=false) int friendCount;
    @Column(nullable=false,updatable=false) boolean referredByFriend;
    @Column(length=500,updatable=false) String referralNote;
    @Column(nullable=false,updatable=false) boolean laptopRentalRequested;
    @Column(updatable=false) Integer baseTotal;
    @Column(updatable=false) Integer discountPercent;
    @Column(updatable=false) Integer periodDiscountPercent;
    @Column(updatable=false) Integer friendDiscountPercent;
    @Column(length=120,updatable=false) String discountLabel;
    @Column(updatable=false) Integer quotedTotal;
    @Column(updatable=false) Integer quotedPerLesson;
}

@Entity @Table(name = "lessons")
class Lesson {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 160) String title;
    @Column(columnDefinition = "TEXT") String description;
    LocalDateTime startsAt;
    @Column(length = 2048) String meetingUrl;
    @Column(nullable = false) boolean published;
    @Column(nullable = false) Instant createdAt = Instant.now();
    public boolean isPublished() { return published; }
}

@Entity @Table(name = "lesson_files")
class LessonFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lesson_id") Lesson lesson;
    @Column(nullable = false, length = 255) String originalName;
    @Column(nullable = false, length = 36, unique = true) String storageName;
    @Column(nullable = false) long size;
    @Column(nullable = false, length = 120) String contentType;
    @Column(nullable = false) Instant createdAt = Instant.now();
}

@Entity @Table(name = "audit_events")
class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 254) String actor;
    @Column(nullable = false, length = 80) String action;
    @Column(nullable = false, length = 160) String target;
    @Column(nullable = false) Instant createdAt = Instant.now();
}
