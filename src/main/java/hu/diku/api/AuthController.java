package hu.diku.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import static hu.diku.api.ApiDtos.*;

@RestController @RequestMapping("/api/auth")
class AuthController {
    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final AccountRepository users;
    private final RateLimiter limiter;
    private final PlatformService service;
    AuthController(AuthenticationManager manager, SecurityContextRepository contexts, CsrfTokenRepository csrf,
        AccountRepository users, RateLimiter limiter, PlatformService service) {
        this.manager=manager; this.contexts=contexts; this.csrf=csrf; this.users=users; this.limiter=limiter; this.service=service;
    }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) {
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }
    @PostMapping("/login") UserDto login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        limiter.check("login:"+request.getRemoteAddr(), 8, Duration.ofMinutes(10));
        Authentication authentication = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.email().strip(),body.password()));
        limiter.reset("login:"+request.getRemoteAddr());
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication,request,response);
        new CsrfAuthenticationStrategy(csrf).onAuthentication(authentication,request,response);
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context); contexts.saveContext(context,request,response);
        var account = users.findById(((AccountPrincipal)authentication.getPrincipal()).id).orElseThrow();
        service.audit(account.email,"LOGIN","user:"+account.id);
        return UserDto.of(account);
    }
    @GetMapping("/me") UserDto me(Authentication authentication) {
        return UserDto.of(users.findById(((AccountPrincipal)authentication.getPrincipal()).id).orElseThrow());
    }
}
