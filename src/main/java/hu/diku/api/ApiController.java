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
    private final RateLimiter limiter;
    private final RegistrationService registrations;
    ApiController(PlatformService service, RateLimiter limiter, RegistrationService registrations) { this.service=service; this.limiter=limiter; this.registrations=registrations; }

    @PostMapping("/applications") @ResponseStatus(HttpStatus.CREATED)
    RegistrationDtos.ApplicationReceipt apply(@Valid @RequestBody ApplicationRequest body, HttpServletRequest request) {
        limiter.check("application:"+request.getRemoteAddr(), 10, Duration.ofHours(1));
        return RegistrationDtos.ApplicationReceipt.of(registrations.submit(body));
    }
    @GetMapping("/courses") List<RegistrationDtos.CourseDto> courses() { return registrations.courses(); }
    @GetMapping("/lessons") List<LessonDto> lessons() {
        return service.listLessons(false);
    }
    @GetMapping("/admin/lessons") List<LessonDto> allLessons() {
        return service.listLessons(true);
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
}
