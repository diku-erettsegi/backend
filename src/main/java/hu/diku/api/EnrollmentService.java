package hu.diku.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnrollmentService {
    record Grant(@NotNull @Positive Long applicationId) {}
    record Access(Long userId, Long applicationId, Long courseId, String courseName, LocalDate expiresOn, boolean active) {}
    private final AccountRepository users;
    private final ApplicationRepository applications;
    private final AuditRepository audit;
    private final Clock clock;
    EnrollmentService(AccountRepository users, ApplicationRepository applications, AuditRepository audit, Clock clock) {
        this.users=users; this.applications=applications; this.audit=audit; this.clock=clock;
    }
    private Access dto(Account user) {
        var a=user.application;
        var c=a==null?null:a.getCourse();
        LocalDate end=c==null||c.getEndDate()==null?null:c.getEndDate().plusMonths(1);
        boolean active=user.enabled && user.role==Role.STUDENT && a!=null && a.getStatus()==ApplicationStatus.ACCEPTED
            && end!=null && !LocalDate.now(clock.withZone(CoursePricing.BUDAPEST)).isAfter(end);
        return new Access(user.id,a==null?null:a.getId(),c==null?null:c.getId(),c==null?null:c.getName(),end,active);
    }
    @Transactional(readOnly=true) Access access(String email) { return dto(users.findByEmailIgnoreCase(email).orElseThrow(PlatformService::missing)); }
    @Transactional(readOnly=true) List<Access> list() { return users.findAllByOrderByNameAsc().stream().map(this::dto).toList(); }
    @Transactional(readOnly=true) boolean canRead(Lesson lesson,String email) {
        var user=users.findByEmailIgnoreCase(email).orElseThrow(PlatformService::missing);
        if(!user.enabled) return false;
        if(user.role==Role.ADMIN) return true;
        var access=dto(user); var course=lesson.getCourse();
        return lesson.isPublished() && access.active() && course!=null && course.getId().equals(access.courseId());
    }
    @Transactional Access grant(long userId, Grant body,String actor) {
        // Serialize duplicate grants on the application before taking the account lock.
        var a=applications.lockById(body.applicationId()).orElseThrow(PlatformService::missing);
        var user=users.lockById(userId).orElseThrow(PlatformService::missing);
        if(user.role!=Role.STUDENT) throw PlatformService.invalid("Tananyag-hozzáférés tanulói fiókhoz rendelhető.");
        if(a.status!=ApplicationStatus.ACCEPTED || a.course==null || a.course.getEndDate()==null)
            throw PlatformService.invalid("Elfogadott jelentkezés és beállított kurzuszáró dátum szükséges.");
        if(users.findByApplicationId(a.id).filter(other->!other.id.equals(user.id)).isPresent())
            throw PlatformService.invalid("Ehhez a jelentkezéshez már tartozik tanulói fiók.");
        user.application=a; user.authVersion++; record(actor,"ENROLLMENT_GRANTED",userId);
        return dto(user);
    }
    @Transactional void revoke(long userId,String actor) {
        var user=users.lockById(userId).orElseThrow(PlatformService::missing);
        user.application=null; user.authVersion++; record(actor,"ENROLLMENT_REVOKED",userId);
    }
    private void record(String actor,String action,long id) {
        var event=new AuditEvent(); event.actor=actor; event.action=action; event.target="user:"+id; audit.save(event);
    }
}
