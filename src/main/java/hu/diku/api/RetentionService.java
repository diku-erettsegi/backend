package hu.diku.api;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded operational retention; contractual/financial evidence is never removed by the 90-day rule. */
@Service
class RetentionService {
    private static final Logger log=LoggerFactory.getLogger(RetentionService.class);
    private final EntityManager entityManager;
    private final CourseRepository courses;
    private final ApplicationRepository applications;
    private final AccountRepository users;
    private final ProgrammingInterestRepository interests;
    private final AuditRepository audit;
    private final Clock clock;
    private final TransactionTemplate transaction;

    RetentionService(EntityManager entityManager,CourseRepository courses,ApplicationRepository applications,
        AccountRepository users,ProgrammingInterestRepository interests,AuditRepository audit,Clock clock,PlatformTransactionManager manager) {
        this.entityManager=entityManager; this.courses=courses; this.applications=applications;
        this.users=users; this.interests=interests; this.audit=audit; this.clock=clock;
        transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    record Result(int closedApplications,int deletedApplications,int deletedInterests,int deletedAccounts,int clearedNotes,int deletedAuditEvents) {}

    @Scheduled(cron="${diku.retention.cron:0 15 3 * * *}",zone="Europe/Budapest")
    void scheduledCleanup() {
        Result result=cleanup();
        // Counts only: never put names, addresses, request bodies or deleted content in the log.
        log.info("Retention completed: closedApplications={}, deletedApplications={}, deletedInterests={}, deletedAccounts={}, clearedNotes={}, deletedAuditEvents={}",
            result.closedApplications(),result.deletedApplications(),result.deletedInterests(),result.deletedAccounts(),result.clearedNotes(),result.deletedAuditEvents());
    }

    Result cleanup() {
        Instant now=clock.instant(), cutoff90=now.minus(Duration.ofDays(90)), cutoff30=now.minus(Duration.ofDays(30));
        LocalDate today=LocalDate.ofInstant(now,CoursePricing.BUDAPEST);
        int closed=0, deletedApplications=0, deletedInterests=0, deletedAccounts=0, cleared=0;

        for(Long id:ids("select a.id from Application a where a.status in (hu.diku.api.ApplicationStatus.NEW,hu.diku.api.ApplicationStatus.CONTACTED,hu.diku.api.ApplicationStatus.WAITLISTED) and a.retentionProtected=false and a.course.endDate is not null")) {
            if(inTransaction(()-> {
                var a=lockApplication(id);
                if(a==null || protectedRecord(a) || !pending(a.status) || a.course==null || a.course.getEndDate()==null || !today.isAfter(a.course.getEndDate())) return false;
                a.status=ApplicationStatus.REJECTED; a.closedAt=now;
                var event=new AuditEvent(); event.actor="system"; event.action="APPLICATION_EXPIRED"; event.target="application:"+a.id; event.createdAt=now; audit.save(event);
                return true;
            })) closed++;
        }

        for(Long id:ids("select u.id from Account u where u.role=hu.diku.api.Role.STUDENT and (u.application is not null or u.accessEndedAt is not null)")) {
            if(inTransaction(()-> {
                // The application -> account ordering is shared with enrollment grants and status updates.
                Long applicationId=entityManager.createQuery("select u.application.id from Account u where u.id=:id and u.application is not null",Long.class).setParameter("id",id).getResultStream().findFirst().orElse(null);
                if(applicationId!=null && lockApplication(applicationId)==null) return false;
                var account=users.lockById(id).orElse(null);
                if(account==null || account.role!=Role.STUDENT || (account.application!=null && !account.application.getId().equals(applicationId))) return false;
                boolean ended=account.accessEndedAt!=null && !account.accessEndedAt.isAfter(cutoff30);
                var course=account.application==null?null:account.application.getCourse();
                ended=ended || (course!=null && accessRetentionExpired(course,today));
                if(!ended) return false;
                users.delete(account); // A missing account immediately invalidates its existing sessions.
                return true;
            })) deletedAccounts++;
        }

        for(Long id:ids("select a.id from Application a where a.closedAt is not null and a.retentionProtected=false")) {
            if(inTransaction(()-> {
                var a=lockApplication(id);
                if(a==null || protectedRecord(a) || !closed(a.status) || a.closedAt==null || a.closedAt.isAfter(cutoff90) || users.findByApplicationId(id).isPresent()) return false;
                applications.delete(a);
                return true;
            })) deletedApplications++;
        }

        for(Long id:ids("select a.id from Application a where a.course.endDate is not null and (a.message is not null or a.notes is not null or a.referralNote is not null)")) {
            if(inTransaction(()-> {
                var a=lockApplication(id);
                if(a==null || a.course==null || !accessRetentionExpired(a.course,today)) return false;
                // Explicit data minimization is the only exception to the immutable referral-note snapshot.
                entityManager.createNativeQuery("UPDATE applications SET message=NULL,notes=NULL,referral_note=NULL WHERE id=:id").setParameter("id",id).executeUpdate();
                return true;
            })) cleared++;
        }

        for(Long id:ids("select i.id from ProgrammingInterest i")) {
            if(inTransaction(()-> {
                var interest=interests.lockById(id).orElse(null);
                if(interest==null) return false;
                Instant start=interest.status==InterestStatus.COMPLETED?interest.completedAt:interest.createdAt;
                if(start==null || start.isAfter(cutoff90)) return false;
                // V2 outbox is unused, but an old row must neither block erasure nor retain a personal link.
                entityManager.createNativeQuery("DELETE FROM mail_outbox WHERE interest_id=:id").setParameter("id",id).executeUpdate();
                interests.delete(interest);
                return true;
            })) deletedInterests++;
        }
        int auditCount=transaction.execute(status->entityManager.createQuery("delete from AuditEvent e where e.createdAt<=:cutoff").setParameter("cutoff",cutoff30).executeUpdate());
        return new Result(closed,deletedApplications,deletedInterests,deletedAccounts,cleared,auditCount);
    }

    private List<Long> ids(String query) { return entityManager.createQuery(query,Long.class).getResultList(); }
    private boolean inTransaction(Supplier<Boolean> work) { return Boolean.TRUE.equals(transaction.execute(status->work.get())); }
    private Application lockApplication(long id) {
        Long courseId=applications.findCourseId(id);
        if(courseId!=null) courses.lockById(courseId);
        var application=applications.lockById(id).orElse(null);
        if(application!=null && application.course!=null && !application.course.getId().equals(courseId)) return null;
        return application;
    }
    private static boolean protectedRecord(Application a) { return a.retentionProtected || a.contractConfirmed || a.paidTrainingTotal>0 || a.fullPaymentDate!=null; }
    private static boolean pending(ApplicationStatus status) { return status==ApplicationStatus.NEW || status==ApplicationStatus.CONTACTED || status==ApplicationStatus.WAITLISTED; }
    private static boolean closed(ApplicationStatus status) { return status==ApplicationStatus.REJECTED || status==ApplicationStatus.WITHDRAWN; }
    private static boolean accessRetentionExpired(Course course,LocalDate today) {
        return course.getEndDate()!=null && today.isAfter(course.getEndDate().plusMonths(1).plusDays(30));
    }
}
