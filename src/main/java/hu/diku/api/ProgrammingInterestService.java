package hu.diku.api;

import static hu.diku.api.RegistrationDtos.*;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
class ProgrammingInterestService {
    private final ProgrammingInterestRepository interests;
    private final PlatformService platform;
    private final Clock clock;
    private final TransactionTemplate transaction;
    ProgrammingInterestService(ProgrammingInterestRepository interests,PlatformService platform,Clock clock,PlatformTransactionManager manager) {
        this.interests=interests; this.platform=platform; this.clock=clock;
        transaction=new TransactionTemplate(manager); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    InterestDto submit(InterestRequest body) {
        String hash=RequestFingerprint.of(PlatformService.clean(body.name()),PlatformService.email(body.email()),body.privacyAccepted());
        try {
            return transaction.execute(status -> {
                var existing=interests.findByRequestId(body.requestId().toString());
                if(existing.isPresent()) return existing(existing.get(),hash);
                var i=new ProgrammingInterest(); i.name=body.name().strip(); i.email=PlatformService.email(body.email());
                i.requestId=body.requestId().toString(); i.requestHash=hash; i.createdAt=clock.instant(); i.privacyAcceptedAt=i.createdAt; i.privacyVersion="programming-preview-draft-2026-10";
                i.updatedAt=i.createdAt;
                interests.saveAndFlush(i);
                platform.audit("public","PROGRAMMING_INTEREST_CREATED","interest:"+i.id);
                return InterestDto.of(i);
            });
        } catch(DataIntegrityViolationException duplicate) {
            // A concurrent request may have committed the same requestId. Re-read only after our transaction rolled back.
            return transaction.execute(status -> {
                var existing=interests.findByRequestId(body.requestId().toString());
                if(existing.isEmpty()) throw duplicate;
                return existing(existing.get(),hash);
            });
        }
    }
    private InterestDto existing(ProgrammingInterest i,String hash) {
        if(!hash.equals(i.requestHash)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Ez a kérésazonosító már más adatokhoz tartozik.");
        return InterestDto.of(i);
    }
    @Transactional(readOnly=true) List<InterestDto> list() { return interests.findAllByOrderByCreatedAtDescIdDesc().stream().map(InterestDto::of).toList(); }
    @Transactional InterestDto update(long interestId,InterestUpdate body,String actor) {
        var interest=interests.lockById(interestId).orElseThrow(PlatformService::missing);
        interest.status=body.status(); interest.notes=PlatformService.clean(body.notes()); interest.updatedAt=clock.instant();
        platform.audit(actor,"PROGRAMMING_INTEREST_UPDATED","interest:"+interestId);
        return InterestDto.of(interests.save(interest));
    }
}
