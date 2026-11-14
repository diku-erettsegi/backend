package hu.diku.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.List;

final class RegistrationDtos {
    private RegistrationDtos() {}
    record PriceDto(int baseTotal, int discountPercent, int periodDiscountPercent, int friendDiscountPercent,
        String discountLabel, int total, int perLesson, LocalDate validUntil) {}
    record CourseDto(Long id, String slug, String name, LocalDate startDate, LocalTime startTime, LocalTime endTime,
        int sessions, int lessonsPerSession, int capacity, long acceptedCount, long availableSeats, boolean registrationOpen, PriceDto price,
        int waitlistCapacity, long waitlistCount, long availableWaitlistSeats, boolean waitlistOpen, LocalDate registrationClosesOn, LocalDate endDate, boolean acceptingApplications, List<LocalDate> sessionDates) {}
    record CourseUpdate(@NotNull @Size(min=20,max=20) List<@NotNull LocalDate> sessionDates, @NotNull Boolean registrationOpen) {}
    record FinanceUpdate(@NotNull Boolean contractConfirmed, @Min(0) @Max(10000000) int paidTrainingTotal, LocalDate fullPaymentDate, @Min(0) @Max(10) int verifiedFriendCount) {}
    record CourseTransfer(@NotNull @Positive Long courseId, @Positive Long swapApplicationId) {}
    record ApplicationReceipt(Long id, String message, String courseName, Integer quotedTotal, Integer quotedPerLesson,
        String discountLabel, Integer discountPercent, Integer periodDiscountPercent, Integer friendDiscountPercent,
        ApplicationStatus status, Integer baseTotal, Integer laptopRentalFee) {
        static ApplicationReceipt of(ApiDtos.ApplicationDto a) {
            return new ApplicationReceipt(a.id(),a.status()==ApplicationStatus.WAITLISTED?"A várólistás jelentkezésed megérkezett.":"A jelentkezésed megérkezett.",a.courseName(),a.quotedTotal(),a.quotedPerLesson(),a.discountLabel(),a.discountPercent(),a.periodDiscountPercent(),a.friendDiscountPercent(),a.status(),a.baseTotal(),a.finance().laptopRentalFee());
        }
    }
    record InterestRequest(@NotBlank @Size(max=120) String name, @NotBlank @Email @Size(max=254) String email,
        @NotNull @AssertTrue Boolean privacyAccepted, @NotNull UUID requestId) {}
    record InterestUpdate(@NotNull InterestStatus status, @Size(max=4000) String notes) {}
    record InterestDto(Long id, String name, String email, Instant createdAt, InterestStatus status, String notes, Instant updatedAt, Instant completedAt, Instant privacyAcceptedAt, String privacyVersion) {
        static InterestDto of(ProgrammingInterest i) { return new InterestDto(i.id,i.name,i.email,i.createdAt,i.status,i.notes,i.updatedAt,i.completedAt,i.privacyAcceptedAt,i.privacyVersion); }
    }
    record InterestReceipt(Long id, String message) {
        static InterestReceipt of(InterestDto interest) {
            return new InterestReceipt(interest.id(),"Az érdeklődésedet rögzítettük. Hamarosan felvesszük veled a kapcsolatot.");
        }
    }
}
