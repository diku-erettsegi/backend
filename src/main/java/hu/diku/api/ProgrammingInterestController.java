package hu.diku.api;

import static hu.diku.api.RegistrationDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
class ProgrammingInterestController {
    private final ProgrammingInterestService service;
    private final RateLimiter limiter;
    ProgrammingInterestController(ProgrammingInterestService service,RateLimiter limiter) { this.service=service; this.limiter=limiter; }
    @PostMapping("/programming-interests") @ResponseStatus(HttpStatus.CREATED)
    InterestReceipt submit(@Valid @RequestBody InterestRequest body,HttpServletRequest request) {
        limiter.check("interest-ip:"+request.getRemoteAddr(),10,Duration.ofHours(1));
        limiter.check("interest-email:"+RequestFingerprint.of(PlatformService.email(body.email())),3,Duration.ofHours(1));
        return InterestReceipt.of(service.submit(body));
    }
    @GetMapping("/admin/programming-interests") List<InterestDto> list() { return service.list(); }
    @PatchMapping("/admin/programming-interests/{id}") InterestDto update(@PathVariable long id,@Valid @RequestBody InterestUpdate body,Authentication auth) { return service.update(id,body,auth.getName()); }
}
