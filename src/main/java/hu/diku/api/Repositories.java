package hu.diku.api;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmailIgnoreCase(String email);
    List<Account> findAllByOrderByNameAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.role = hu.diku.api.Role.ADMIN and a.enabled = true")
    List<Account> lockEnabledAdmins();
}
interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findAllByOrderByCreatedAtDesc();
    List<Application> findByCourseIdOrderByCreatedAtDesc(Long courseId);
    Optional<Application> findByRequestId(String requestId);
    long countByCourseIdAndStatus(Long courseId, ApplicationStatus status);
    @Query("select a.course.id from Application a where a.id = :id") Long findCourseId(long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id") Optional<Application> lockById(long id);
}
interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findAllByOrderByStartsAtAscIdAsc();
    List<Lesson> findByPublishedTrueOrderByStartsAtAscIdAsc();
}
interface LessonFileRepository extends JpaRepository<LessonFile, Long> {
    List<LessonFile> findByLessonIdOrderByIdAsc(Long lessonId);
}
interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop200ByOrderByCreatedAtDescIdDesc();
}
