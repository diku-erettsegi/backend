package hu.diku.api;

import static hu.diku.api.ApiDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
class ApiController {
    private final PlatformService service;
    private final EnrollmentService enrollment;
    private final RateLimiter limiter;
    private final RegistrationService registrations;
    ApiController(PlatformService service, RateLimiter limiter, RegistrationService registrations, EnrollmentService enrollment) { this.enrollment=enrollment; this.service=service; this.limiter=limiter; this.registrations=registrations; }

    @PostMapping("/applications") @ResponseStatus(HttpStatus.CREATED)
    RegistrationDtos.ApplicationReceipt apply(@Valid @RequestBody ApplicationRequest body, HttpServletRequest request) {
        limiter.check("application:"+request.getRemoteAddr(), 10, Duration.ofHours(1));
        return RegistrationDtos.ApplicationReceipt.of(registrations.submit(body));
    }
    @GetMapping("/courses") List<RegistrationDtos.CourseDto> courses() { return registrations.courses(); }
    @GetMapping("/lessons") List<LessonDto> lessons(Authentication auth) {
        return service.listLessons(false,auth.getName());
    }
    @GetMapping("/admin/lessons") List<LessonDto> allLessons(Authentication auth) {
        return service.listLessons(true,auth.getName());
    }
    @PostMapping("/admin/lessons") @ResponseStatus(HttpStatus.CREATED)
    LessonDto createLesson(@Valid @RequestBody LessonRequest body, Authentication auth) { return service.saveLesson(null,body,auth.getName()); }
    @PutMapping("/admin/lessons/{id}") LessonDto updateLesson(@PathVariable long id, @Valid @RequestBody LessonRequest body, Authentication auth) { return service.saveLesson(id,body,auth.getName()); }
    @GetMapping("/admin/applications") List<ApplicationDto> applications(@RequestParam(required=false) Long courseId) { return registrations.list(courseId); }
    @PatchMapping("/admin/applications/{id}") ApplicationDto updateApplication(@PathVariable long id, @Valid @RequestBody ApplicationUpdate body, Authentication auth) { return registrations.update(id,body,auth.getName()); }
    @GetMapping("/admin/users") List<UserDto> users() { return service.listUsers(); }
    @PostMapping("/admin/users") @ResponseStatus(HttpStatus.CREATED)
    UserDto createUser(@Valid @RequestBody CreateUser body, Authentication auth) { return service.createUser(body,auth.getName()); }
    @PatchMapping("/admin/users/{id}") UserDto updateUser(@PathVariable long id, @Valid @RequestBody UpdateUser body, Authentication auth) { return service.updateUser(id,body,auth.getName()); }
    @GetMapping("/admin/audit") List<AuditDto> audit() { return service.listAudit(); }
    @PutMapping("/admin/courses/{id}") RegistrationDtos.CourseDto updateCourse(@PathVariable long id,@Valid @RequestBody RegistrationDtos.CourseUpdate body,Authentication auth) { return registrations.updateCourse(id,body,auth.getName()); }
    @PutMapping("/admin/applications/{id}/finance") ApplicationDto finance(@PathVariable long id,@Valid @RequestBody RegistrationDtos.FinanceUpdate body,Authentication auth) { return registrations.updateFinance(id,body,auth.getName()); }
    @PutMapping("/admin/applications/{id}/course") ApplicationDto transfer(@PathVariable long id,@Valid @RequestBody RegistrationDtos.CourseTransfer body,Authentication auth) { return registrations.transfer(id,body,auth.getName()); }
    @GetMapping("/access") EnrollmentService.Access access(Authentication auth) { return enrollment.access(auth.getName()); }
    @GetMapping("/admin/enrollments") List<EnrollmentService.Access> enrollments() { return enrollment.list(); }
    @PutMapping("/admin/users/{id}/enrollment") EnrollmentService.Access grant(@PathVariable long id,@Valid @RequestBody EnrollmentService.Grant body,Authentication auth) { return enrollment.grant(id,body,auth.getName()); }
    @DeleteMapping("/admin/users/{id}/enrollment") @ResponseStatus(HttpStatus.NO_CONTENT) void revoke(@PathVariable long id,Authentication auth) { enrollment.revoke(id,auth.getName()); }
}
