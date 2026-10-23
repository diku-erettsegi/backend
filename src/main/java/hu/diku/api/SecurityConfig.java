package hu.diku.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService userDetailsService(AccountRepository users) {
        return email -> new AccountPrincipal(users.findByEmailIgnoreCase(email.strip())
            .orElseThrow(() -> new UsernameNotFoundException("Ismeretlen felhasználó.")));
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SecurityFilterChain security(HttpSecurity http, AccountRepository users, ObjectMapper json,
        SecurityContextRepository contexts, CsrfTokenRepository csrf) throws Exception {
        http.csrf(c -> c.csrfTokenRepository(csrf))
            .securityContext(c -> c.securityContextRepository(contexts))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/applications", "/api/courses", "/api/programming-interests", "/actuator/health", "/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .requestCache(c -> c.disable())
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable())
            .logout(c -> c.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request,response,auth) -> response.setStatus(204)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request,response,error) -> writeError(response,json,401,"A folytatáshoz jelentkezz be."))
                .accessDeniedHandler((request,response,error) -> writeError(response,json,403,"Nincs jogosultságod, vagy lejárt a biztonsági token. Frissítsd az oldalt.")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                    response.setHeader("Cache-Control", "no-store");
                    var auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
                        var account = users.findById(principal.id).orElse(null);
                        if (account == null || !account.enabled || account.authVersion != principal.version) {
                            SecurityContextHolder.clearContext();
                            var session = request.getSession(false);
                            if (session != null) session.invalidate();
                            writeError(response,json,401,"A munkamenet lejárt. Jelentkezz be újra.");
                            return;
                        }
                    }
                    chain.doFilter(request,response);
                }
            }, AuthorizationFilter.class);
        return http.build();
    }
    private static void writeError(HttpServletResponse response, ObjectMapper json, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        json.writeValue(response.getWriter(), Map.of("message",message));
    }
}

final class AccountPrincipal implements UserDetails {
    final Long id;
    final long version;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;
    AccountPrincipal(Account a) {
        id = a.id; version = a.authVersion; email = a.email; password = a.passwordHash; enabled = a.enabled;
        authorities = List.of(new SimpleGrantedAuthority("ROLE_" + a.role));
    }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return password; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public List<GrantedAuthority> getAuthorities() { return authorities; }
}
