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

@SpringBootTest
@AutoConfigureMockMvc @ActiveProfiles("local") @Testcontainers @Import(RegistrationIntegrationTest.TestTime.class)
class RegistrationIntegrationTest {
    @Container static MariaDBContainer<?> db=new MariaDBContainer<>("mariadb:11.4");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        // Rehearse a real V1 -> V2 upgrade, including a pre-existing application, before Spring applies V2.
        Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).target("1").load().migrate();
        try(var connection=DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword()); var statement=connection.createStatement()) {
            statement.executeUpdate("INSERT INTO applications(student_name,email,phone,status,privacy_accepted_at,privacy_version,created_at) VALUES ('Régi teszt jelentkezés','legacy@example.test','123','NEW','2026-10-17 10:00:00','draft-2026-10','2026-10-17 10:00:00')");
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

    @BeforeEach void resetState() {
        jdbc.update("DELETE FROM applications WHERE request_id IS NOT NULL");
        jdbc.update("UPDATE courses SET capacity=24, registration_open=TRUE");
        jdbc.update("DELETE FROM mail_outbox"); jdbc.update("DELETE FROM programming_interests");
        clock.set("2026-10-20T10:00:00Z");
    }
    ApiDtos.ApplicationRequest application(long courseId,UUID requestId) {
        return new ApiDtos.ApplicationRequest("Teszt diák","student@example.test","+36301234567",null,null,true,courseId,requestId,0,null,true,false);
    }
    RegistrationDtos.InterestRequest interest(UUID id) { return new RegistrationDtos.InterestRequest("Teszt érdeklődő","preview@example.test",true,id); }
    void accepted(long id) { registrations.update(id,new ApiDtos.ApplicationUpdate(ApplicationStatus.ACCEPTED,null),"test-admin"); }

    @Test void upgradePreservesLegacyApplicationsAndCreatesExactlyTwoGroups() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].slug").value("szombat-delelott")).andExpect(jsonPath("$[0].startDate").value("2027-01-09"))
            .andExpect(jsonPath("$[0].startTime").value("09:00:00")).andExpect(jsonPath("$[1].startTime").value("14:00:00"))
            .andExpect(jsonPath("$[0].capacity").value(24)).andExpect(jsonPath("$[0].sessions").value(20)).andExpect(jsonPath("$[0].lessonsPerSession").value(4));
        var legacy=registrations.list(null).stream().filter(a->a.email().equals("legacy@example.test")).findFirst().orElseThrow();
        assertThat(legacy.courseId()).isNull(); assertThat(legacy.quotedTotal()).isNull();
        assertThat(legacy.createdAt()).isEqualTo(Instant.parse("2026-10-17T10:00:00Z"));
        assertThat(legacy.studentName()).isEqualTo("Régi teszt jelentkezés");
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
        var body=new ApiDtos.ApplicationRequest("Teszt diák","test@example.test","123",null,null,true,1L,UUID.randomUUID(),0,"Meghívott egy barát",false,true);
        var saved=registrations.submit(body);
        assertThat(saved.periodDiscountPercent()).isEqualTo(25); assertThat(saved.friendDiscountPercent()).isEqualTo(5);
        assertThat(saved.discountPercent()).isEqualTo(30); assertThat(saved.quotedTotal()).isEqualTo(196000);
        assertThat(saved.referredByFriend()).isTrue(); assertThat(saved.friendDiscountStatus()).isEqualTo("PENDING_ADMIN");
        assertThat(registrations.submit(body).id()).isEqualTo(saved.id());
        var changed=new ApiDtos.ApplicationRequest(body.studentName(),body.email(),body.phone(),null,null,true,1L,body.requestId(),0,body.referralNote(),false,false);
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
}
