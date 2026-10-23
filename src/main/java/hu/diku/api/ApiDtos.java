package hu.diku.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}
    public record LoginRequest(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    public record UserDto(Long id, String name, String email, Role role, boolean enabled) {
        static UserDto of(Account a) { return new UserDto(a.id, a.name, a.email, a.role, a.enabled); }
    }
    public record CreateUser(@NotBlank @Size(max=120) String name, @NotBlank @Email @Size(max=254) String email,
                             @NotBlank @Size(min=12,max=72) String password, @NotNull Role role, Boolean enabled) {}
    public record UpdateUser(@Size(min=1,max=120) String name, @Email @Size(min=3,max=254) String email,
                             Role role, Boolean enabled, @Size(min=12,max=72) String password) {}
    public record ApplicationRequest(@NotBlank @Size(max=120) String studentName,
        @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=40) String phone,
        @Size(max=120) String parentName, @Size(max=4000) String message,
        @NotNull @AssertTrue Boolean privacyAccepted, @NotNull @Positive Long courseId,
        @NotNull UUID requestId, @NotNull @Min(0) @Max(10) Integer friendCount,
        @Size(max=500) String referralNote, @NotNull Boolean laptopRentalRequested, Boolean referredByFriend) {}
    public record ApplicationUpdate(@NotNull ApplicationStatus status, @Size(max=4000) String notes) {}
    public record ApplicationDto(Long id, String studentName, String email, String phone, String parentName,
        String message, ApplicationStatus status, String notes, Instant createdAt, Instant privacyAcceptedAt, String privacyVersion,
        Long courseId, String courseName, int friendCount, String referralNote, boolean laptopRentalRequested, boolean referredByFriend,
        String friendDiscountStatus, Integer baseTotal, Integer discountPercent, Integer periodDiscountPercent, Integer friendDiscountPercent,
        String discountLabel, Integer quotedTotal, Integer quotedPerLesson) {
        static ApplicationDto of(Application a) { return new ApplicationDto(a.id,a.studentName,a.email,a.phone,a.parentName,a.message,a.status,a.notes,a.createdAt,a.privacyAcceptedAt,a.privacyVersion,
            a.course==null?null:a.course.getId(),a.course==null?null:a.course.getName(),a.friendCount,a.referralNote,a.laptopRentalRequested,a.referredByFriend,
            a.friendCount>0||a.referredByFriend?"PENDING_ADMIN":"NOT_REQUESTED",a.baseTotal,a.discountPercent,a.periodDiscountPercent,a.friendDiscountPercent,a.discountLabel,a.quotedTotal,a.quotedPerLesson); }
    }
    public record LessonRequest(@NotBlank @Size(max=160) String title, @Size(max=6000) String description,
        LocalDateTime startsAt, @Size(max=2048) String meetingUrl, boolean published) {}
    public record FileDto(Long id, String originalName, long size, String contentType) {
        static FileDto of(LessonFile f) { return new FileDto(f.id,f.originalName,f.size,f.contentType); }
    }
    public record LessonDto(Long id, String title, String description, LocalDateTime startsAt, String meetingUrl, boolean published, List<FileDto> files) {}
    public record AuditDto(Long id, String actor, String action, String target, Instant createdAt) {
        static AuditDto of(AuditEvent e) { return new AuditDto(e.id,e.actor,e.action,e.target,e.createdAt); }
    }
}
