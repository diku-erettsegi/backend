package hu.diku.api;

import static hu.diku.api.ApiDtos.*;
import static hu.diku.api.RegistrationDtos.*;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class RegistrationService {
    private final CourseRepository courses;
    private final ApplicationRepository applications;
    private final CoursePricing pricing;
    private final Clock clock;
    private final PlatformService platform;
    RegistrationService(CourseRepository courses,ApplicationRepository applications,CoursePricing pricing,Clock clock,PlatformService platform) {
        this.courses=courses; this.applications=applications; this.pricing=pricing; this.clock=clock; this.platform=platform;
    }
    @Transactional(readOnly=true) List<CourseDto> courses() {
        return courses.findAllByOrderByStartTimeAsc().stream().map(c -> {
            long accepted=applications.countByCourseIdAndStatus(c.id,ApplicationStatus.ACCEPTED);
            long seats=Math.max(0,c.capacity-accepted);
            return new CourseDto(c.id,c.slug,c.name,c.startDate,c.startTime,c.endTime,c.sessions,c.lessonsPerSession,c.capacity,accepted,seats,c.registrationOpen&&seats>0,pricing.price(c));
        }).toList();
    }
    @Transactional(readOnly=true) List<ApplicationDto> list(Long courseId) {
        if(courseId!=null && !courses.existsById(courseId)) throw PlatformService.missing();
        return (courseId==null?applications.findAllByOrderByCreatedAtDesc():applications.findByCourseIdOrderByCreatedAtDesc(courseId))
            .stream().map(ApplicationDto::of).toList();
    }
    /** Course lock serializes submissions and acceptance; READ_COMMITTED avoids an old count after waiting for the lock. */
    @Transactional(isolation=Isolation.READ_COMMITTED) ApplicationDto submit(ApplicationRequest body) {
        String hash=RequestFingerprint.of(PlatformService.clean(body.studentName()),PlatformService.email(body.email()),
            PlatformService.clean(body.phone()),PlatformService.clean(body.parentName()),PlatformService.clean(body.message()),
            body.courseId(),body.friendCount(),PlatformService.clean(body.referralNote()),body.laptopRentalRequested(),body.privacyAccepted());
        boolean referredByFriend=Boolean.TRUE.equals(body.referredByFriend());
        // Preserve V2 fingerprints for unchanged pre-V3 requests; the new true flag changes the fingerprint.
        if(referredByFriend) hash=RequestFingerprint.of(hash,"referredByFriend:true");
        var course=courses.lockById(body.courseId()).orElseThrow(PlatformService::missing);
        var existing=applications.findByRequestId(body.requestId().toString());
        if(existing.isPresent()) {
            if(!hash.equals(existing.get().requestHash)) throw conflict("Ez a kérésazonosító már más adatokhoz tartozik.");
            return ApplicationDto.of(existing.get());
        }
        if(!course.registrationOpen) throw conflict("Ennek a csoportnak a jelentkezése lezárult.");
        requireSeat(course);
        var createdAt=clock.instant();
        var quote=pricing.priceAt(course,body.friendCount(),referredByFriend,createdAt);
        var a=new Application(); a.studentName=body.studentName().strip(); a.email=PlatformService.email(body.email());
        a.phone=body.phone().strip(); a.parentName=PlatformService.clean(body.parentName()); a.message=PlatformService.clean(body.message());
        a.course=course; a.requestId=body.requestId().toString(); a.requestHash=hash;
        a.friendCount=body.friendCount(); a.referralNote=PlatformService.clean(body.referralNote()); a.laptopRentalRequested=body.laptopRentalRequested();
        a.referredByFriend=referredByFriend;
        a.baseTotal=quote.baseTotal(); a.discountPercent=quote.discountPercent(); a.discountLabel=quote.discountLabel();
        a.periodDiscountPercent=quote.periodDiscountPercent(); a.friendDiscountPercent=quote.friendDiscountPercent();
        a.quotedTotal=quote.total(); a.quotedPerLesson=quote.perLesson();
        a.createdAt=createdAt; a.privacyAcceptedAt=a.createdAt;
        applications.saveAndFlush(a); platform.audit("public","APPLICATION_CREATED","application:"+a.id);
        return ApplicationDto.of(a);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) ApplicationDto update(long id,ApplicationUpdate body,String actor) {
        // Always lock the group before its application. This order is shared by all registration writes.
        Long courseId=applications.findCourseId(id);
        Course course=courseId==null?null:courses.lockById(courseId).orElseThrow(PlatformService::missing);
        var a=applications.lockById(id).orElseThrow(PlatformService::missing);
        if(body.status()==ApplicationStatus.ACCEPTED && a.status!=ApplicationStatus.ACCEPTED && course!=null) requireSeat(course);
        a.status=body.status(); a.notes=PlatformService.clean(body.notes());
        platform.audit(actor,"APPLICATION_UPDATED","application:"+id);
        return ApplicationDto.of(applications.saveAndFlush(a));
    }
    private void requireSeat(Course course) {
        if(applications.countByCourseIdAndStatus(course.id,ApplicationStatus.ACCEPTED)>=course.capacity)
            throw conflict("A csoport betelt. Válassz másik csoportot, vagy vedd fel velünk a kapcsolatot.");
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
