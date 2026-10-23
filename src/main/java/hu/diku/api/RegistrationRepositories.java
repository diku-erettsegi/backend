package hu.diku.api;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CourseRepository extends JpaRepository<Course,Long> {
    List<Course> findAllByOrderByStartTimeAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id") Optional<Course> lockById(long id);
}
interface ProgrammingInterestRepository extends JpaRepository<ProgrammingInterest,Long> {
    Optional<ProgrammingInterest> findByRequestId(String requestId);
    List<ProgrammingInterest> findAllByOrderByCreatedAtDescIdDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProgrammingInterest i where i.id = :id") Optional<ProgrammingInterest> lockById(long id);
}
