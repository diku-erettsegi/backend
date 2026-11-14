package hu.diku.api;

import static hu.diku.api.ApiDtos.*;
import static hu.diku.api.RegistrationDtos.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class RegistrationService {
    static final int WAITLIST_CAPACITY=5;
    private final CourseRepository courses;
    private final ApplicationRepository applications;
    private final CoursePricing pricing;
    private final Clock clock;
    private final PlatformService platform;
    private final AccountRepository users;
    RegistrationService(CourseRepository courses,ApplicationRepository applications,CoursePricing pricing,Clock clock,PlatformService platform,AccountRepository users) {
        this.courses=courses; this.applications=applications; this.pricing=pricing; this.clock=clock; this.platform=platform; this.users=users;
    }
    @Transactional(readOnly=true) List<CourseDto> courses() {
        return courses.findAllByOrderByStartTimeAsc().stream().map(c -> {
            long accepted=applications.countByCourseIdAndStatus(c.id,ApplicationStatus.ACCEPTED);
            long seats=Math.max(0,c.capacity-accepted);
            long waiting=applications.countByCourseIdAndStatus(c.id,ApplicationStatus.WAITLISTED);
            boolean open=isOpen(c);
            return new CourseDto(c.id,c.slug,c.name,c.startDate,c.startTime,c.endTime,c.sessions,c.lessonsPerSession,c.capacity,accepted,seats,open&&seats>0,pricing.price(c),
                WAITLIST_CAPACITY,waiting,Math.max(0,WAITLIST_CAPACITY-waiting),open&&seats==0&&waiting<WAITLIST_CAPACITY,c.startDate.plusWeeks(2),c.endDate,c.registrationOpen,List.copyOf(c.sessionDates));
        }).toList();
    }
    private boolean isOpen(Course course) {
        return course.registrationOpen && !LocalDate.now(clock.withZone(CoursePricing.BUDAPEST)).isAfter(course.startDate.plusWeeks(2));
    }
    @Transactional CourseDto updateCourse(long id,CourseUpdate body,String actor) {
        var c=courses.lockById(id).orElseThrow(PlatformService::missing);
        var dates=body.sessionDates();
        if(dates.size()!=c.sessions || !dates.getFirst().equals(c.startDate) || dates.stream().distinct().count()!=dates.size()
            || !dates.equals(dates.stream().sorted().toList())) throw PlatformService.invalid("A 20 dátum legyen időrendben, ismétlés nélkül, az indulás napjától.");
        c.sessionDates.clear(); c.sessionDates.addAll(dates); c.endDate=dates.getLast(); c.registrationOpen=body.registrationOpen();
        courses.saveAndFlush(c); platform.audit(actor,"COURSE_UPDATED","course:"+id);
        return courses().stream().filter(dto->dto.id().equals(id)).findFirst().orElseThrow();
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
        if(body.ageGroup()!=null || body.guardianConfirmed()!=null || body.waitlistRequested()!=null)
            hash=RequestFingerprint.of(hash,body.ageGroup(),Boolean.TRUE.equals(body.guardianConfirmed()),Boolean.TRUE.equals(body.waitlistRequested()));
        var course=courses.lockById(body.courseId()).orElseThrow(PlatformService::missing);
        var existing=applications.findByRequestId(body.requestId().toString());
        if(existing.isPresent()) {
            if(!hash.equals(existing.get().requestHash)) throw conflict("Ez a kérésazonosító már más adatokhoz tartozik.");
            return ApplicationDto.of(existing.get());
        }
        if(body.ageGroup()==null) throw PlatformService.invalid("Válaszd ki a tanuló életkori kategóriáját (14–17 vagy 18 év felett).");
        if(body.ageGroup()==AgeGroup.MINOR_14_17 && (!Boolean.TRUE.equals(body.guardianConfirmed()) || body.parentName()==null || body.parentName().isBlank()))
            throw PlatformService.invalid("Kiskorú tanuló jelentkezését a szülő/gondviselő adhatja be, saját nevének megadásával és nyilatkozatával.");
        if(!isOpen(course)) throw conflict("Az általános jelentkezés lezárult. Későbbi csatlakozáshoz keresd a szervezőket.");
        boolean waiting=applications.countByCourseIdAndStatus(course.id,ApplicationStatus.ACCEPTED)>=course.capacity;
        if(waiting) {
            if(!Boolean.TRUE.equals(body.waitlistRequested())) throw conflict("A csoport betelt. Kérhetsz felvételt az 5 fős várólistára.");
            requireWaitlistSeat(course);
        }
        var createdAt=clock.instant();
        var quote=pricing.priceAt(course,body.friendCount(),referredByFriend,createdAt);
        var a=new Application(); a.studentName=body.studentName().strip(); a.email=PlatformService.email(body.email());
        a.phone=body.phone().strip(); a.parentName=PlatformService.clean(body.parentName()); a.message=PlatformService.clean(body.message());
        a.course=course; a.requestId=body.requestId().toString(); a.requestHash=hash;
        a.friendCount=body.friendCount(); a.referralNote=PlatformService.clean(body.referralNote()); a.laptopRentalRequested=body.laptopRentalRequested();
        a.referredByFriend=referredByFriend;
        a.laptopRentalFee=body.laptopRentalRequested()?30000:0;
        a.ageGroup=body.ageGroup(); a.guardianConfirmed=Boolean.TRUE.equals(body.guardianConfirmed()); a.waitlistRequested=Boolean.TRUE.equals(body.waitlistRequested());
        a.status=waiting?ApplicationStatus.WAITLISTED:ApplicationStatus.NEW;
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
        if(courseId!=null && !courseId.equals(a.course.getId())) throw conflict("A jelentkezés csoportja közben megváltozott. Töltsd újra az adatokat.");
        if(body.status()==ApplicationStatus.ACCEPTED && a.status!=ApplicationStatus.ACCEPTED) {
            if(!hasRequiredAdvance(a)) throw PlatformService.invalid("Elfogadás előtt igazold az írásos szerződést és legalább az igazolt képzési díj 20%-ának befizetését.");
            if(course!=null) requireSeat(course);
        }
        if(body.status()==ApplicationStatus.WAITLISTED && a.status!=ApplicationStatus.WAITLISTED) {
            if(course==null) throw PlatformService.invalid("Várólistához csoport szükséges.");
            requireWaitlistSeat(course);
        }
        if(a.status==ApplicationStatus.WAITLISTED && (body.status()==ApplicationStatus.NEW || body.status()==ApplicationStatus.CONTACTED) && course!=null) requireSeat(course);
        boolean closed=body.status()==ApplicationStatus.REJECTED || body.status()==ApplicationStatus.WITHDRAWN;
        if(closed && a.closedAt==null) a.closedAt=clock.instant();
        else if(!closed) a.closedAt=null;
        if(a.status!=body.status() && (a.status==ApplicationStatus.ACCEPTED || body.status()==ApplicationStatus.ACCEPTED)) {
            users.lockByApplicationId(id).ifPresent(account -> {
                account.accessEndedAt=body.status()==ApplicationStatus.ACCEPTED?null:clock.instant();
                account.authVersion++;
            });
        }
        a.status=body.status(); a.notes=PlatformService.clean(body.notes());
        platform.audit(actor,"APPLICATION_UPDATED","application:"+id);
        return ApplicationDto.of(applications.saveAndFlush(a));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) ApplicationDto updateFinance(long id,FinanceUpdate body,String actor) {
        Long courseId=applications.findCourseId(id);
        if(courseId!=null) courses.lockById(courseId).orElseThrow(PlatformService::missing);
        var a=applications.lockById(id).orElseThrow(PlatformService::missing);
        if(courseId!=null && !courseId.equals(a.course.getId())) throw conflict("A csoport közben megváltozott. Töltsd újra az adatokat.");
        if(body.fullPaymentDate()!=null && (body.fullPaymentDate().isAfter(LocalDate.now(clock.withZone(CoursePricing.BUDAPEST)))
            || body.fullPaymentDate().isBefore(a.createdAt.atZone(CoursePricing.BUDAPEST).toLocalDate())))
            throw PlatformService.invalid("A teljes befizetés napja a jelentkezés és a mai nap közé essen.");
        a.contractConfirmed=body.contractConfirmed(); a.paidTrainingTotal=body.paidTrainingTotal();
        a.fullPaymentDate=body.fullPaymentDate(); a.verifiedFriendCount=body.verifiedFriendCount();
        a.retentionProtected=a.retentionProtected || a.contractConfirmed || a.paidTrainingTotal>0;
        if(a.status==ApplicationStatus.ACCEPTED && !hasRequiredAdvance(a))
            throw PlatformService.invalid("Előbb vond vissza az elfogadott státuszt; elfogadott jelentkezésnél szerződés és legalább 20% igazolt befizetés szükséges.");
        var summary=FinancialSummary.of(a);
        if(a.fullPaymentDate!=null && (summary.trainingTotal()==null || a.paidTrainingTotal<summary.trainingTotal()))
            throw PlatformService.invalid("A teljes befizetés dátuma csak a kedvezményekkel számított teljes képzési díj beérkezésekor adható meg.");
        platform.audit(actor,"APPLICATION_FINANCE_UPDATED","application:"+id);
        return ApplicationDto.of(applications.saveAndFlush(a));
    }
    private static boolean hasRequiredAdvance(Application application) {
        Integer advance=FinancialSummary.of(application).minimumAdvanceTotal();
        return application.contractConfirmed && application.paidTrainingTotal>0
            && (advance==null || application.paidTrainingTotal>=advance);
    }
    private void requireSeat(Course course) {
        if(applications.countByCourseIdAndStatus(course.id,ApplicationStatus.ACCEPTED)>=course.capacity)
            throw conflict("A csoport betelt. Válassz másik csoportot, vagy vedd fel velünk a kapcsolatot.");
    }
    private void requireWaitlistSeat(Course course) {
        if(applications.countByCourseIdAndStatus(course.id,ApplicationStatus.WAITLISTED)>=WAITLIST_CAPACITY)
            throw conflict("A csoport 5 fős várólistája is betelt. Keresd a szervezőket.");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) ApplicationDto transfer(long id,CourseTransfer body,String actor) {
        Long sourceId=applications.findCourseId(id);
        if(sourceId==null) throw PlatformService.invalid("Csoport nélküli korábbi jelentkezés nem mozgatható ezzel a művelettel.");
        if(sourceId.equals(body.courseId())) throw PlatformService.invalid("Másik csoportot válassz.");
        // Every transfer takes course and application locks in ascending order, before changing either row.
        var lockedCourses=new HashMap<Long,Course>();
        for(Long key:List.of(sourceId,body.courseId()).stream().sorted().toList())
            lockedCourses.put(key,courses.lockById(key).orElseThrow(PlatformService::missing));
        var ids=body.swapApplicationId()==null?List.of(id):List.of(id,body.swapApplicationId()).stream().distinct().sorted().toList();
        var locked=new HashMap<Long,Application>();
        for(Long key:ids) locked.put(key,applications.lockById(key).orElseThrow(PlatformService::missing));
        var a=locked.get(id); var target=lockedCourses.get(body.courseId());
        if(!sourceId.equals(a.course.getId())) throw conflict("A csoport közben megváltozott. Töltsd újra az adatokat.");
        if(body.swapApplicationId()!=null) {
            var other=locked.get(body.swapApplicationId());
            if(other.id.equals(id) || other.course==null || !target.id.equals(other.course.getId()) || a.status!=ApplicationStatus.ACCEPTED || other.status!=ApplicationStatus.ACCEPTED)
                throw PlatformService.invalid("Cseréhez a másik csoport egy elfogadott jelentkezőjét válaszd, és mindkét jelentkezés legyen elfogadott.");
            other.course=lockedCourses.get(sourceId);
            platform.audit(actor,"APPLICATION_GROUP_SWAPPED","application:"+other.id);
        } else {
            requireSeat(target);
            if(a.status==ApplicationStatus.WAITLISTED) a.status=ApplicationStatus.CONTACTED;
        }
        a.course=target; applications.flush();
        platform.audit(actor,"APPLICATION_GROUP_CHANGED","application:"+a.id);
        return ApplicationDto.of(a);
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
