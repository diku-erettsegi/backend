package hu.diku.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties="diku.retention.cron=-")
@AutoConfigureMockMvc @ActiveProfiles("local") @Testcontainers @Import(RegistrationIntegrationTest.TestTime.class)
class RegistrationIntegrationTest {
    @Container static MariaDBContainer<?> db=new MariaDBContainer<>("mariadb:11.4");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        // Rehearse a real V1 -> V2 upgrade, including a pre-existing application, before Spring applies V2.
        Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).target("1").load().migrate();
        try(var connection=DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword()); var statement=connection.createStatement()) {
            statement.executeUpdate("INSERT INTO applications(student_name,email,phone,status,privacy_accepted_at,privacy_version,created_at) VALUES ('Régi teszt jelentkezés','legacy@example.test','123','REJECTED','2026-10-17 10:00:00','draft-2026-10','2026-10-17 10:00:00')");
        }
        Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).target("2").load().migrate();
        try(var connection=DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword()); var statement=connection.createStatement()) {
            statement.executeUpdate("INSERT INTO applications(student_name,email,phone,status,privacy_accepted_at,privacy_version,created_at,course_id,friend_count,base_total,discount_percent,discount_label,quoted_total,quoted_per_lesson) VALUES ('V2 ármegőrzés','v2@example.test','123','NEW','2026-10-20 10:00:00','draft-2026-10','2026-10-20 10:00:00',2,2,280000,25,'Korábbi 25%',210000,2625)");
        }
        registry.add("spring.datasource.url",db::getJdbcUrl); registry.add("spring.datasource.username",db::getUsername); registry.add("spring.datasource.password",db::getPassword);
        String storage=Files.createTempDirectory("diku-registration-test-").toString(); registry.add("diku.storage-path",()->storage);
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time=new AtomicReference<>(Instant.parse("2026-10-20T10:00:00Z"));
        void set(String value) { time.set(Instant.parse(value)); }
        void advance(Duration duration) { time.updateAndGet(i->i.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
        @Override public Instant instant() { return time.get(); }
    }
    @TestConfiguration static class TestTime { @Bean @Primary MutableClock mutableClock() { return new MutableClock(); } }
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registrations;
    @Autowired ProgrammingInterestService interests;
    @Autowired ProgrammingInterestRepository interestRepository;
    @Autowired ApplicationRepository applications;
    @Autowired EnrollmentService enrollment;
    @Autowired AccountRepository users;
    @Autowired PlatformService platform;
    @Autowired FileService files;
    @Autowired RetentionService retention;
    @Autowired AuditRepository audit;

    @BeforeEach void resetState() {
        jdbc.update("UPDATE users SET application_id=NULL,access_ended_at=NULL");
        jdbc.update("DELETE FROM users WHERE email LIKE 'retention-%@example.test'");
        jdbc.update("DELETE FROM applications WHERE request_id IS NOT NULL");
        // Migration fixtures are retained across test methods; production never runs this test-only preservation.
        jdbc.update("UPDATE applications SET retention_protected=TRUE WHERE request_id IS NULL");
        jdbc.update("UPDATE courses SET capacity=24, registration_open=TRUE");
        jdbc.update("DELETE FROM mail_outbox"); jdbc.update("DELETE FROM programming_interests");
        clock.set("2026-10-20T10:00:00Z");
    }
    ApiDtos.ApplicationRequest application(long courseId,UUID requestId) {
        return new ApiDtos.ApplicationRequest("Teszt diák","student@example.test","+36301234567",null,null,true,courseId,requestId,0,null,true,false,AgeGroup.ADULT,false,false);
    }
    RegistrationDtos.InterestRequest interest(UUID id) { return new RegistrationDtos.InterestRequest("Teszt érdeklődő","preview@example.test",true,id); }
    void accepted(long id) { registrations.updateFinance(id,new RegistrationDtos.FinanceUpdate(true,280000,null,0),"test-admin"); registrations.update(id,new ApiDtos.ApplicationUpdate(ApplicationStatus.ACCEPTED,null),"test-admin"); }

    @Test void minorRequiresGuardianAndNewAgeDeclaration() {
        var missing=new ApiDtos.ApplicationRequest("Kiskorú","minor@example.test","123",null,null,true,1L,UUID.randomUUID(),0,null,false,false,AgeGroup.MINOR_14_17,false,false);
        assertThatThrownBy(()->registrations.submit(missing)).isInstanceOf(ResponseStatusException.class);
        var valid=new ApiDtos.ApplicationRequest("Kiskorú","parent@example.test","123","Teszt szülő",null,true,1L,UUID.randomUUID(),0,null,true,false,AgeGroup.MINOR_14_17,true,false);
        var saved=registrations.submit(valid);
        assertThat(saved.guardianConfirmed()).isTrue(); assertThat(saved.finance().laptopRentalFee()).isEqualTo(30000);
        assertThat(registrations.submit(valid).id()).isEqualTo(saved.id());
    }

    @Test void waitlistHasFiveSlotsUnderConcurrentSubmissions() throws Exception {
        jdbc.update("UPDATE courses SET capacity=1 WHERE id=1"); accepted(registrations.submit(application(1,UUID.randomUUID())).id());
        try(var pool=Executors.newFixedThreadPool(8)) {
            var jobs=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<12;i++) jobs.add(()-> {
                var body=new ApiDtos.ApplicationRequest("Várólista","wait@example.test","123",null,null,true,1L,UUID.randomUUID(),0,null,false,false,AgeGroup.ADULT,false,true);
                try { return registrations.submit(body).status()==ApplicationStatus.WAITLISTED; }
                catch(ResponseStatusException e) { assertThat(e.getStatusCode().value()).isEqualTo(409); return false; }
            });
            int success=0; for(var result:pool.invokeAll(jobs)) if(result.get()) success++;
            assertThat(success).isEqualTo(5);
        }
        assertThat(registrations.courses().getFirst().waitlistOpen()).isFalse();
        assertThat(applications.countByCourseIdAndStatus(1L,ApplicationStatus.WAITLISTED)).isEqualTo(5);
    }

    @Test void paymentDeadlineRequiresFullAmountAndKeepsOriginalQuote() {
        var a=registrations.submit(application(1,UUID.randomUUID()));
        assertThatThrownBy(()->registrations.update(a.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.ACCEPTED,null),"test")).isInstanceOf(ResponseStatusException.class);
        var partial=registrations.updateFinance(a.id(),new RegistrationDtos.FinanceUpdate(true,21000,null,0),"test");
        assertThat(partial.finance().periodDiscountPercent()).isZero(); assertThat(partial.finance().trainingTotal()).isEqualTo(280000);
        assertThatThrownBy(()->registrations.updateFinance(a.id(),new RegistrationDtos.FinanceUpdate(true,21000,LocalDate.of(2026,10,20),0),"test")).isInstanceOf(ResponseStatusException.class);
        clock.set("2026-11-16T12:00:00Z");
        var timely=registrations.updateFinance(a.id(),new RegistrationDtos.FinanceUpdate(true,210000,LocalDate.of(2026,11,15),0),"test");
        assertThat(timely.finance().periodDiscountPercent()).isEqualTo(25);
        var late=registrations.updateFinance(a.id(),new RegistrationDtos.FinanceUpdate(true,238000,LocalDate.of(2026,11,16),0),"test");
        assertThat(late.finance().periodDiscountPercent()).isEqualTo(15);
        assertThat(late.quotedTotal()).isEqualTo(210000); assertThat(late.createdAt()).isEqualTo(a.createdAt());
        var friends=registrations.updateFinance(a.id(),new RegistrationDtos.FinanceUpdate(true,238000,LocalDate.of(2026,11,16),10),"test");
        assertThat(friends.finance().discountPercent()).isEqualTo(35);
    }

    @Test void registrationClosesAfterTwoWeeksButRetrySurvives() {
        clock.set("2027-01-23T22:59:59Z"); var body=application(1,UUID.randomUUID()); var a=registrations.submit(body);
        clock.set("2027-01-23T23:00:00Z");
        assertThat(registrations.courses().getFirst().registrationOpen()).isFalse();
        assertThat(registrations.submit(body).id()).isEqualTo(a.id());
        assertThatThrownBy(()->registrations.submit(application(1,UUID.randomUUID()))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void groupTransferNeedsSeatOrAcceptedSwapPartner() {
        jdbc.update("UPDATE courses SET capacity=1");
        var a=registrations.submit(application(1,UUID.randomUUID())); var b=registrations.submit(application(2,UUID.randomUUID())); accepted(a.id()); accepted(b.id());
        assertThatThrownBy(()->registrations.transfer(a.id(),new RegistrationDtos.CourseTransfer(2L,null),"test")).isInstanceOf(ResponseStatusException.class);
        var moved=registrations.transfer(a.id(),new RegistrationDtos.CourseTransfer(2L,b.id()),"test");
        assertThat(moved.courseId()).isEqualTo(2); assertThat(moved.quotedTotal()).isEqualTo(a.quotedTotal());
        assertThat(registrations.list(1L)).anyMatch(row->row.id().equals(b.id()));
        assertThat(registrations.courses()).allMatch(c->c.acceptedCount()==1);
    }

    @Test void lessonsAndFileBytesRespectGroupAcceptanceAndExpiry() throws Exception {
        var a=registrations.submit(application(1,UUID.randomUUID()));
        var student=users.findByEmailIgnoreCase("diak@diku.local").orElseThrow();
        assertThatThrownBy(()->enrollment.grant(student.id,new EnrollmentService.Grant(a.id()),"test")).isInstanceOf(ResponseStatusException.class);
        accepted(a.id()); enrollment.grant(student.id,new EnrollmentService.Grant(a.id()),"test");
        var own=platform.saveLesson(null,new ApiDtos.LessonRequest("Saját csoport","",null,null,true,1L),"test");
        var other=platform.saveLesson(null,new ApiDtos.LessonRequest("Másik csoport","",null,null,true,2L),"test");
        var upload=new org.springframework.mock.web.MockMultipartFile("file","minta.txt","text/plain","private bytes".getBytes());
        var ownFile=files.upload(own.id(),upload,"test"); var otherFile=files.upload(other.id(),upload,"test");
        assertThat(platform.listLessons(false,student.email)).extracting(ApiDtos.LessonDto::id).contains(own.id()).doesNotContain(other.id());
        assertThat(Files.readString(files.download(ownFile.id(),student.email).path())).isEqualTo("private bytes");
        assertThatThrownBy(()->files.download(otherFile.id(),student.email)).isInstanceOf(ResponseStatusException.class);
        clock.set("2027-06-29T21:59:59Z"); assertThat(enrollment.access(student.email).active()).isTrue();
        clock.set("2027-06-29T22:00:00Z"); assertThat(enrollment.access(student.email).active()).isFalse();
        assertThat(platform.listLessons(false,student.email)).isEmpty();
        assertThatThrownBy(()->files.download(ownFile.id(),student.email)).isInstanceOf(ResponseStatusException.class);
        clock.set("2027-06-01T10:00:00Z"); registrations.update(a.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.REJECTED,null),"test");
        assertThatThrownBy(()->files.download(ownFile.id(),student.email)).isInstanceOf(ResponseStatusException.class);
        files.deleteLesson(own.id(),"test"); files.deleteLesson(other.id(),"test");
    }

    @Test void upgradePreservesLegacyApplicationsAndCreatesExactlyTwoGroups() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].slug").value("szombat-delelott")).andExpect(jsonPath("$[0].startDate").value("2027-01-09"))
            .andExpect(jsonPath("$[0].startTime").value("09:00:00")).andExpect(jsonPath("$[1].startTime").value("14:00:00"))
            .andExpect(jsonPath("$[0].capacity").value(24)).andExpect(jsonPath("$[0].sessions").value(20)).andExpect(jsonPath("$[0].lessonsPerSession").value(4));
        var legacy=registrations.list(null).stream().filter(a->a.email().equals("legacy@example.test")).findFirst().orElseThrow();
        assertThat(legacy.courseId()).isNull(); assertThat(legacy.quotedTotal()).isNull();
        assertThat(legacy.createdAt()).isEqualTo(Instant.parse("2026-10-17T10:00:00Z"));
        assertThat(legacy.studentName()).isEqualTo("Régi teszt jelentkezés");
        assertThat(legacy.privacyVersion()).isEqualTo("draft-2026-10");
        assertThat(legacy.closedAt()).isAfter(legacy.createdAt());
        var previousQuote=registrations.list(2L).stream().filter(a->a.email().equals("v2@example.test")).findFirst().orElseThrow();
        assertThat(previousQuote.quotedTotal()).isEqualTo(210000); assertThat(previousQuote.discountPercent()).isEqualTo(25);
        assertThat(previousQuote.periodDiscountPercent()).isEqualTo(25); assertThat(previousQuote.friendDiscountPercent()).isZero();
    }
    @Test void pricesSwitchAtBudapestMidnightAndQuotesStayImmutable() throws Exception {
        clock.set("2026-11-15T22:59:59Z");
        var body=application(1L,UUID.randomUUID()); var saved=registrations.submit(body);
        assertThat(saved.discountPercent()).isEqualTo(25); assertThat(saved.quotedTotal()).isEqualTo(210000); assertThat(saved.quotedPerLesson()).isEqualTo(2625);
        clock.set("2026-11-15T23:00:00Z");
        mvc.perform(get("/api/courses")).andExpect(jsonPath("$[0].price.discountPercent").value(15)).andExpect(jsonPath("$[0].price.total").value(238000)).andExpect(jsonPath("$[0].price.perLesson").value(2975));
        var again=registrations.submit(body); assertThat(again.id()).isEqualTo(saved.id()); assertThat(again.quotedTotal()).isEqualTo(210000);
        assertThat(again.createdAt()).isEqualTo(saved.createdAt());
        clock.set("2026-12-15T22:59:59Z"); assertThat(registrations.courses().getFirst().price().discountPercent()).isEqualTo(15);
        clock.set("2026-12-15T23:00:00Z");
        mvc.perform(get("/api/courses")).andExpect(jsonPath("$[0].price.discountPercent").value(0)).andExpect(jsonPath("$[0].price.total").value(280000)).andExpect(jsonPath("$[0].price.perLesson").value(3500)).andExpect(jsonPath("$[0].price.validUntil").isEmpty());
        var updated=registrations.update(saved.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.CONTACTED,"Telefonáltunk"),"test-admin");
        assertThat(updated.quotedTotal()).isEqualTo(210000); assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
        assertThat(updated.friendDiscountStatus()).isEqualTo("NOT_REQUESTED");
    }
    @Test void courseFilteringValidationAndQuoteReceiptAreEnforced() throws Exception {
        var first=application(1,UUID.randomUUID()); registrations.submit(application(2,UUID.randomUUID()));
        String body=json.writeValueAsString(first);
        mvc.perform(post("/api/applications").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/applications").with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.quotedTotal").value(210000)).andExpect(jsonPath("$.courseName").value("Szombat délelőtti csoport"));
        mvc.perform(get("/api/admin/applications").param("courseId","1").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].courseId").value(1));
        mvc.perform(get("/api/admin/applications").with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/applications").with(csrf()).contentType("application/json").content(body.replace("\"friendCount\":0","\"friendCount\":11"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/applications").with(csrf()).contentType("application/json").content(body.replace("\"courseId\":1","\"courseId\":null"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/applications").with(csrf()).contentType("application/json").content(body.replace("Teszt diák","Másik név"))).andExpect(status().isConflict());
    }
    @Test void simultaneousAcceptancesCannotExceedCapacityAndPendingDoesNotReserve() throws Exception {
        List<Long> ids=new ArrayList<>();
        for(int i=0;i<30;i++) ids.add(registrations.submit(application(1,UUID.randomUUID())).id());
        assertThat(registrations.courses().getFirst().availableSeats()).isEqualTo(24);
        try(var executor=Executors.newFixedThreadPool(10)) {
            var futures=ids.stream().map(id -> executor.submit(() -> {
                try { accepted(id); return 200; } catch(ResponseStatusException full) { return full.getStatusCode().value(); }
            })).toList();
            List<Integer> results=new ArrayList<>(); for(var future:futures) results.add(future.get(30,TimeUnit.SECONDS));
            assertThat(Collections.frequency(results,200)).isEqualTo(24); assertThat(Collections.frequency(results,409)).isEqualTo(6);
        }
        assertThat(applications.countByCourseIdAndStatus(1L,ApplicationStatus.ACCEPTED)).isEqualTo(24);
        assertThat(registrations.courses().getFirst().availableSeats()).isZero(); assertThat(registrations.courses().getFirst().registrationOpen()).isFalse();
        assertThatThrownBy(()->registrations.submit(application(1,UUID.randomUUID()))).isInstanceOf(ResponseStatusException.class).extracting(e->((ResponseStatusException)e).getStatusCode().value()).isEqualTo(409);
        long acceptedId=registrations.list(1L).stream().filter(a->a.status()==ApplicationStatus.ACCEPTED).findFirst().orElseThrow().id();
        accepted(acceptedId); // Idempotent acceptance is valid even when full.
        registrations.update(acceptedId,new ApiDtos.ApplicationUpdate(ApplicationStatus.REJECTED,null),"test-admin");
        assertThat(registrations.courses().getFirst().availableSeats()).isEqualTo(1);
        assertThat(registrations.submit(application(1,UUID.randomUUID())).status()).isEqualTo(ApplicationStatus.NEW);
        jdbc.update("UPDATE courses SET registration_open=FALSE WHERE id=2");
        assertThatThrownBy(()->registrations.submit(application(2,UUID.randomUUID()))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void concurrentIdenticalApplicationRetriesCreateOneRow() throws Exception {
        var body=application(1,UUID.randomUUID());
        try(var executor=Executors.newFixedThreadPool(6)) {
            var tasks=new ArrayList<Future<Long>>();
            for(int i=0;i<6;i++) tasks.add(executor.submit(()->registrations.submit(body).id()));
            Set<Long> ids=new HashSet<>(); for(var task:tasks) ids.add(task.get(30,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
        }
        assertThat(registrations.list(1L)).hasSize(1);
    }
    @Test void stackedDiscountsRespectCapAndReferredFriendDoesNotDoubleCount() {
        int[] friendCounts={0,1,2,3,10};
        int[] expected={25,30,35,35,35};
        for(int index=0;index<friendCounts.length;index++) {
            var quote=CoursePricing.price(280000,80,LocalDate.of(2026,11,15),friendCounts[index],false);
            assertThat(quote.discountPercent()).isEqualTo(expected[index]);
        }
        var one=CoursePricing.price(280000,80,LocalDate.of(2026,11,15),1,true);
        assertThat(one.discountPercent()).isEqualTo(30); assertThat(one.total()).isEqualTo(196000); assertThat(one.perLesson()).isEqualTo(2450);
        var invited=CoursePricing.price(280000,80,LocalDate.of(2026,11,15),0,true);
        assertThat(invited).isEqualTo(one);
        var max=CoursePricing.price(280000,80,LocalDate.of(2026,11,15),10,true);
        assertThat(max.discountPercent()).isEqualTo(35); assertThat(max.friendDiscountPercent()).isEqualTo(50);
        assertThat(max.total()).isEqualTo(182000); assertThat(max.perLesson()).isEqualTo(2275);
        var late=CoursePricing.price(280000,80,LocalDate.of(2026,12,16),1,false);
        assertThat(late.periodDiscountPercent()).isZero(); assertThat(late.discountPercent()).isEqualTo(5);
        var mid=CoursePricing.price(280000,80,LocalDate.of(2026,12,15),1,false);
        assertThat(mid.discountPercent()).isEqualTo(20);
        var body=new ApiDtos.ApplicationRequest("Teszt diák","test@example.test","123",null,null,true,1L,UUID.randomUUID(),0,"Meghívott egy barát",false,true,AgeGroup.ADULT,false,false);
        var saved=registrations.submit(body);
        assertThat(saved.periodDiscountPercent()).isEqualTo(25); assertThat(saved.friendDiscountPercent()).isEqualTo(5);
        assertThat(saved.discountPercent()).isEqualTo(30); assertThat(saved.quotedTotal()).isEqualTo(196000);
        assertThat(saved.referredByFriend()).isTrue(); assertThat(saved.friendDiscountStatus()).isEqualTo("PENDING_ADMIN");
        assertThat(registrations.submit(body).id()).isEqualTo(saved.id());
        var changed=new ApiDtos.ApplicationRequest(body.studentName(),body.email(),body.phone(),null,null,true,1L,body.requestId(),0,body.referralNote(),false,false,AgeGroup.ADULT,false,false);
        assertThatThrownBy(()->registrations.submit(changed)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void manualInterestsAreDurableIdempotentAndOnlyAdminsCanManageThem() throws Exception {
        var body=interest(UUID.randomUUID()); String content=json.writeValueAsString(body);
        mvc.perform(post("/api/programming-interests").contentType("application/json").content(content)).andExpect(status().isForbidden());
        mvc.perform(post("/api/programming-interests").with(csrf()).contentType("application/json").content(content.replace("true","false"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/programming-interests").with(csrf()).contentType("application/json").content(content)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.deliveryStatus").doesNotExist());
        var first=interests.submit(body); var again=interests.submit(body);
        assertThat(again.id()).isEqualTo(first.id()); assertThat(interestRepository.count()).isEqualTo(1);
        assertThat(first.status()).isEqualTo(InterestStatus.NEW); assertThat(first.createdAt()).isEqualTo(clock.instant());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_outbox",Long.class)).isZero();
        mvc.perform(get("/api/admin/programming-interests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/programming-interests").with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/programming-interests").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("NEW"));
        clock.advance(Duration.ofHours(1));
        String update="{\"status\":\"CONTACTED\",\"notes\":\"Manuálisan kapcsolatba léptünk.\"}";
        mvc.perform(patch("/api/admin/programming-interests/"+first.id()).with(user("student").roles("STUDENT")).with(csrf()).contentType("application/json").content(update)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/programming-interests/"+first.id()).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(update))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTACTED")).andExpect(jsonPath("$.notes").value("Manuálisan kapcsolatba léptünk."));
        var updated=interests.list().getFirst(); assertThat(updated.updatedAt()).isEqualTo(clock.instant()); assertThat(updated.createdAt()).isEqualTo(first.createdAt());
        interests.update(first.id(),new RegistrationDtos.InterestUpdate(InterestStatus.COMPLETED,"Lezárva"),"test-admin");
        assertThat(interests.list().getFirst().status()).isEqualTo(InterestStatus.COMPLETED);
        assertThat(interests.submit(body).status()).isEqualTo(InterestStatus.COMPLETED); // Replays cannot reset administrative work.
        mvc.perform(post("/api/admin/programming-interests/"+first.id()+"/retry-email").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void concurrentInterestRetriesCreateOneRecordAndChangedPayloadConflicts() throws Exception {
        var body=interest(UUID.randomUUID());
        try(var executor=Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<Future<Long>>(); for(int i=0;i<4;i++) tasks.add(executor.submit(()->interests.submit(body).id()));
            Set<Long> ids=new HashSet<>(); for(var task:tasks) ids.add(task.get(30,TimeUnit.SECONDS)); assertThat(ids).hasSize(1);
        }
        assertThat(interestRepository.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_outbox",Long.class)).isZero();
        assertThatThrownBy(()->interests.submit(new RegistrationDtos.InterestRequest("Más név",body.email(),true,body.requestId())))
            .isInstanceOf(ResponseStatusException.class).extracting(e->((ResponseStatusException)e).getStatusCode().value()).isEqualTo(409);
    }

    @Test void acceptanceRequiresTwentyPercentOfVerifiedPriceAndFinanceCannotBypassIt() {
        var a=registrations.submit(application(1,UUID.randomUUID()));
        assertThat(a.privacyVersion()).isEqualTo("launch-2026-11-13");
        var finance=registrations.updateFinance(a.id(),new RegistrationDtos.FinanceUpdate(true,55999,null,0),"test");
        assertThat(finance.finance().minimumAdvanceTotal()).isEqualTo(56000);
        assertThatThrownBy(()->registrations.update(a.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.ACCEPTED,null),"test")).isInstanceOf(ResponseStatusException.class);
        registrations.updateFinance(a.id(),new RegistrationDtos.FinanceUpdate(true,56000,null,0),"test");
        registrations.update(a.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.ACCEPTED,null),"test");
        assertThatThrownBy(()->registrations.updateFinance(a.id(),new RegistrationDtos.FinanceUpdate(true,55999,null,0),"test")).isInstanceOf(ResponseStatusException.class);
        assertThat(applications.findById(a.id()).orElseThrow().paidTrainingTotal).isEqualTo(56000);
        var discounted=registrations.submit(application(2,UUID.randomUUID()));
        var discountedFinance=registrations.updateFinance(discounted.id(),new RegistrationDtos.FinanceUpdate(true,50400,null,2),"test");
        assertThat(discountedFinance.finance().minimumAdvanceTotal()).isEqualTo(50400);
        registrations.update(discounted.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.ACCEPTED,null),"test");
    }

    @Test void closedIntentRetentionUsesClosureTimeAndPreservesAnyHistoricalContractOrPayment() {
        var removable=registrations.submit(application(1,UUID.randomUUID()));
        var contractual=registrations.submit(application(1,UUID.randomUUID()));
        var paid=registrations.submit(application(1,UUID.randomUUID()));
        registrations.updateFinance(contractual.id(),new RegistrationDtos.FinanceUpdate(true,0,null,0),"test");
        registrations.updateFinance(contractual.id(),new RegistrationDtos.FinanceUpdate(false,0,null,0),"test");
        registrations.updateFinance(paid.id(),new RegistrationDtos.FinanceUpdate(false,1,null,0),"test");
        registrations.updateFinance(paid.id(),new RegistrationDtos.FinanceUpdate(false,0,null,0),"test");
        var closed=registrations.update(removable.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.WITHDRAWN,"Visszavonta"),"test");
        registrations.update(contractual.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.REJECTED,null),"test");
        registrations.update(paid.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.WITHDRAWN,null),"test");
        clock.advance(Duration.ofDays(89));
        var edited=registrations.update(removable.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.WITHDRAWN,"Megjegyzés javítása"),"test");
        assertThat(edited.closedAt()).isEqualTo(closed.closedAt());
        retention.cleanup(); assertThat(applications.existsById(removable.id())).isTrue();
        clock.advance(Duration.ofDays(1));
        assertThat(retention.cleanup().deletedApplications()).isEqualTo(1);
        assertThat(applications.existsById(removable.id())).isFalse();
        assertThat(applications.existsById(contractual.id())).isTrue(); assertThat(applications.existsById(paid.id())).isTrue();
    }

    @Test void reopeningAnIntentResetsItsClosureAndUnconfirmedIntentsCloseAfterCourseEnd() {
        var a=registrations.submit(application(1,UUID.randomUUID()));
        registrations.update(a.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.REJECTED,null),"test");
        clock.advance(Duration.ofDays(89));
        var open=registrations.update(a.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.CONTACTED,null),"test");
        assertThat(open.closedAt()).isNull();
        clock.set("2027-05-29T12:00:00Z"); retention.cleanup(); assertThat(applications.findById(a.id()).orElseThrow().status).isEqualTo(ApplicationStatus.CONTACTED);
        clock.set("2027-05-30T12:00:00Z"); assertThat(retention.cleanup().closedApplications()).isEqualTo(1);
        assertThat(applications.findById(a.id()).orElseThrow().closedAt).isEqualTo(clock.instant());
        clock.advance(Duration.ofDays(90)); retention.cleanup(); assertThat(applications.existsById(a.id())).isFalse();
    }

    @Test void interestRetentionDeletesOldOpenRequestsAndCompletedRequestsAfterTheirOwnGracePeriod() {
        var pending=interests.submit(interest(UUID.randomUUID()));
        var completed=interests.submit(interest(UUID.randomUUID()));
        assertThat(interestRepository.findById(pending.id()).orElseThrow().privacyVersion).isEqualTo("programming-preview-2026-11-13");
        clock.advance(Duration.ofDays(10));
        var closed=interests.update(completed.id(),new RegistrationDtos.InterestUpdate(InterestStatus.COMPLETED,"Link kézzel elküldve"),"test");
        jdbc.update("INSERT INTO mail_outbox(interest_id,preview_url,next_attempt_at,created_at) VALUES (?, 'https://example.test/preview',NOW(),NOW())",completed.id());
        clock.advance(Duration.ofDays(80));
        assertThat(retention.cleanup().deletedInterests()).isEqualTo(1);
        assertThat(interestRepository.existsById(pending.id())).isFalse();
        assertThat(interests.update(completed.id(),new RegistrationDtos.InterestUpdate(InterestStatus.COMPLETED,"Korrigált megjegyzés"),"test").completedAt()).isEqualTo(closed.completedAt());
        clock.advance(Duration.ofDays(10));
        assertThat(retention.cleanup().deletedInterests()).isEqualTo(1);
        assertThat(interestRepository.existsById(completed.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_outbox",Long.class)).isZero();
    }

    @Test void expiredStudentAccountsAndOptionalNotesAreRemovedWithoutErasingFinancialRecords() throws Exception {
        var a=registrations.submit(new ApiDtos.ApplicationRequest("Teszt diák","retention-student@example.test","123",null,"Személyes kérdés",true,1L,UUID.randomUUID(),0,"Ajánló neve",false,false,AgeGroup.ADULT,false,false));
        accepted(a.id());
        var student=platform.createUser(new ApiDtos.CreateUser("Teszt tanuló","retention-student@example.test","RetentionTest2026!",Role.STUDENT,true),"test");
        enrollment.grant(student.id(),new EnrollmentService.Grant(a.id()),"test");
        registrations.update(a.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.ACCEPTED,"Tanulási megjegyzés"),"test");
        var session=(org.springframework.mock.web.MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\"retention-student@example.test\",\"password\":\"RetentionTest2026!\"}"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        clock.set("2027-07-29T21:59:59Z"); retention.cleanup(); assertThat(users.existsById(student.id())).isTrue();
        clock.set("2027-07-29T22:00:00Z");
        var result=retention.cleanup(); assertThat(result.deletedAccounts()).isEqualTo(1); assertThat(result.clearedNotes()).isEqualTo(1);
        assertThat(users.existsById(student.id())).isFalse();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        var kept=applications.findById(a.id()).orElseThrow(); assertThat(kept.contractConfirmed).isTrue(); assertThat(kept.paidTrainingTotal).isEqualTo(280000);
        assertThat(kept.studentName).isEqualTo("Teszt diák"); assertThat(kept.quotedTotal).isEqualTo(210000);
        assertThat(kept.message).isNull(); assertThat(kept.notes).isNull(); assertThat(kept.referralNote).isNull();
        assertThat(users.findByEmailIgnoreCase("admin@diku.local")).isPresent();
    }

    @Test void revokedAccessIsDeletedAfterThirtyDaysButGrantingAgainCancelsDeletion() {
        var a=registrations.submit(application(1,UUID.randomUUID())); accepted(a.id());
        var student=platform.createUser(new ApiDtos.CreateUser("Visszavont tanuló","retention-revoked@example.test","RetentionTest2026!",Role.STUDENT,true),"test");
        enrollment.grant(student.id(),new EnrollmentService.Grant(a.id()),"test"); enrollment.revoke(student.id(),"test");
        clock.advance(Duration.ofDays(29)); retention.cleanup(); assertThat(users.existsById(student.id())).isTrue();
        enrollment.grant(student.id(),new EnrollmentService.Grant(a.id()),"test");
        clock.advance(Duration.ofDays(1)); retention.cleanup(); assertThat(users.existsById(student.id())).isTrue();
        registrations.update(a.id(),new ApiDtos.ApplicationUpdate(ApplicationStatus.WITHDRAWN,null),"test");
        clock.advance(Duration.ofDays(30)); retention.cleanup(); assertThat(users.existsById(student.id())).isFalse();
        assertThat(applications.existsById(a.id())).isTrue();
    }

    @Test void auditEventsExpireAtThirtyDaysAndCleanupIsRepeatable() {
        var old=new AuditEvent(); old.actor="retention-test"; old.action="TEST"; old.target="test:1"; old.createdAt=clock.instant().minus(Duration.ofDays(30)); audit.save(old);
        var recent=new AuditEvent(); recent.actor="retention-test"; recent.action="TEST"; recent.target="test:2"; recent.createdAt=clock.instant().minus(Duration.ofDays(30)).plusSeconds(1); audit.save(recent);
        retention.cleanup(); assertThat(audit.existsById(old.id)).isFalse(); assertThat(audit.existsById(recent.id)).isTrue();
        var again=retention.cleanup(); assertThat(again.deletedAuditEvents()).isZero(); assertThat(again.deletedAccounts()).isZero(); assertThat(again.deletedApplications()).isZero();
    }

    @Test void interestConsentWithdrawalCanBeErasedImmediatelyOnlyByAdminWithCsrf() throws Exception {
        var interest=interests.submit(interest(UUID.randomUUID()));
        jdbc.update("INSERT INTO mail_outbox(interest_id,preview_url,next_attempt_at,created_at) VALUES (?, 'https://example.test/preview',NOW(),NOW())",interest.id());
        String url="/api/admin/programming-interests/"+interest.id();
        mvc.perform(delete(url).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete(url).with(csrf()).with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(delete(url).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(delete(url).with(csrf()).with(user("admin").roles("ADMIN"))).andExpect(status().isNoContent());
        assertThat(interestRepository.existsById(interest.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mail_outbox",Long.class)).isZero();
        assertThat(audit.findTop200ByOrderByCreatedAtDescIdDesc()).anyMatch(event->event.action.equals("PROGRAMMING_INTEREST_DELETED") && event.target.equals("interest:"+interest.id()));
        mvc.perform(delete(url).with(csrf()).with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
    }
}
