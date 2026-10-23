package hu.diku.api;

import static hu.diku.api.ApiDtos.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class PlatformService {
    final AccountRepository users;
    final ApplicationRepository applications;
    final LessonRepository lessons;
    final LessonFileRepository files;
    final AuditRepository events;
    final PasswordEncoder encoder;
    PlatformService(AccountRepository users, ApplicationRepository applications, LessonRepository lessons,
        LessonFileRepository files, AuditRepository events, PasswordEncoder encoder) {
        this.users=users; this.applications=applications; this.lessons=lessons; this.files=files; this.events=events; this.encoder=encoder;
    }
    static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"A kért adat nem található."); }
    static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    static String clean(String value) { return value == null ? null : value.strip(); }
    static String email(String value) { return value.strip().toLowerCase(Locale.ROOT); }
    @Transactional(readOnly=true) List<UserDto> listUsers() { return users.findAllByOrderByNameAsc().stream().map(UserDto::of).toList(); }
    @Transactional(readOnly=true) List<AuditDto> listAudit() { return events.findTop200ByOrderByCreatedAtDescIdDesc().stream().map(AuditDto::of).toList(); }
    @Transactional(readOnly=true) List<LessonDto> listLessons(boolean admin) {
        return (admin ? lessons.findAllByOrderByStartsAtAscIdAsc() : lessons.findByPublishedTrueOrderByStartsAtAscIdAsc()).stream().map(this::dto).toList();
    }
    static void validatePassword(String value) {
        if (value.length()<12 || value.getBytes(StandardCharsets.UTF_8).length>72)
            throw invalid("A jelszó legalább 12 karakter és legfeljebb 72 UTF-8 bájt lehet.");
    }
    @Transactional void audit(String actor, String action, String target) {
        var e=new AuditEvent(); e.actor=actor; e.action=action; e.target=target; events.save(e);
    }
    @Transactional UserDto createUser(CreateUser body, String actor) {
        validatePassword(body.password());
        String email=email(body.email());
        if(users.findByEmailIgnoreCase(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Ez az e-mail-cím már foglalt.");
        var a=new Account(); a.name=body.name().strip(); a.email=email; a.passwordHash=encoder.encode(body.password()); a.role=body.role();
        a.enabled=!Boolean.FALSE.equals(body.enabled());
        users.save(a); audit(actor,"USER_CREATED","user:"+a.id); return UserDto.of(a);
    }
    @Transactional UserDto updateUser(long id, UpdateUser body, String actor) {
        List<Account> admins=users.lockEnabledAdmins();
        var a=users.findById(id).orElseThrow(PlatformService::missing);
        if(a.role==Role.ADMIN && a.enabled && admins.size()==1 && (body.role()==Role.STUDENT || Boolean.FALSE.equals(body.enabled())))
            throw invalid("Az utolsó aktív adminisztrátor nem tiltható le és nem fokozható le.");
        if(body.name()!=null) { if(body.name().isBlank()) throw invalid("A név nem lehet üres."); a.name=body.name().strip(); }
        if(body.email()!=null) {
            String address=email(body.email());
            if(address.isBlank()) throw invalid("Az e-mail-cím nem lehet üres.");
            if(users.findByEmailIgnoreCase(address).filter(other -> !other.id.equals(a.id)).isPresent())
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Ez az e-mail-cím már foglalt.");
            if(!a.email.equals(address)) a.authVersion++;
            a.email=address;
        }
        if(body.password()!=null) { validatePassword(body.password()); a.passwordHash=encoder.encode(body.password()); a.authVersion++; }
        if(body.role()!=null && body.role()!=a.role) { a.role=body.role(); a.authVersion++; }
        if(body.enabled()!=null && body.enabled()!=a.enabled) { a.enabled=body.enabled(); a.authVersion++; }
        audit(actor,"USER_UPDATED","user:"+id); return UserDto.of(users.save(a));
    }
    @Transactional(readOnly=true) LessonDto dto(Lesson l) {
        return new LessonDto(l.id,l.title,l.description,l.startsAt,l.meetingUrl,l.published,
            files.findByLessonIdOrderByIdAsc(l.id).stream().map(FileDto::of).toList());
    }
    @Transactional LessonDto saveLesson(Long id, LessonRequest body, String actor) {
        String link=clean(body.meetingUrl());
        if(link!=null && !link.isBlank()) {
            try { var uri=URI.create(link); if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException(); }
            catch(IllegalArgumentException e) { throw invalid("Az értekezlet hivatkozása érvényes HTTPS-cím legyen."); }
        }
        var l=id==null?new Lesson():lessons.findById(id).orElseThrow(PlatformService::missing);
        l.title=body.title().strip(); l.description=clean(body.description()); l.startsAt=body.startsAt(); l.meetingUrl=link; l.published=body.published();
        lessons.save(l); audit(actor,id==null?"LESSON_CREATED":"LESSON_UPDATED","lesson:"+l.id); return dto(l);
    }
}
