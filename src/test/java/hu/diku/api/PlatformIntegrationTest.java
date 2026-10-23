package hu.diku.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Testcontainers
class PlatformIntegrationTest {
    @Container static MariaDBContainer<?> db=new MariaDBContainer<>("mariadb:11.4");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {
        r.add("spring.datasource.url",db::getJdbcUrl); r.add("spring.datasource.username",db::getUsername); r.add("spring.datasource.password",db::getPassword);
        String storage=Files.createTempDirectory("diku-test-").toString(); r.add("diku.storage-path",()->storage);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    MockHttpSession login(String email,String password) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("email",email,"password",password))))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    MockHttpSession admin() throws Exception { return login("admin@diku.local","DikuAdmin2026!"); }
    JsonNode parse(String text) throws Exception { return json.readTree(text); }

    @Test void csrfAndApplicationPersistence() throws Exception {
        mvc.perform(get("/api/admin/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        String body=json.writeValueAsString(Map.of("studentName","Teszt diák","email","test@example.com","phone","+36 30 111 1111","privacyAccepted",true,
            "courseId",1,"requestId",UUID.randomUUID().toString(),"friendCount",0,"laptopRentalRequested",false));
        mvc.perform(post("/api/applications").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/applications").with(csrf()).contentType("application/json").content(body.replace("true","false"))).andExpect(status().isBadRequest());
        var response=mvc.perform(post("/api/applications").with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn();
        long id=parse(response.getResponse().getContentAsString()).get("id").asLong();
        var session=admin();
        mvc.perform(patch("/api/admin/applications/"+id).session(session).with(csrf()).contentType("application/json").content("{\"status\":\"CONTACTED\",\"notes\":\"Teszt\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTACTED"));
        mvc.perform(get("/api/admin/audit").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].action").exists());
    }
    @Test void draftFilesAreProtectedAndPublishedFilesDownload() throws Exception {
        var admin=admin(); var student=login("diak@diku.local","DikuDiak2026!");
        mvc.perform(get("/api/admin/users").session(student)).andExpect(status().isForbidden());
        String lesson="{\"title\":\"Minta tesztóra\",\"description\":\"Teszt\",\"startsAt\":\"2026-11-01T15:00:00\",\"published\":false}";
        long id=parse(mvc.perform(post("/api/admin/lessons").session(admin).with(csrf()).contentType("application/json").content(lesson)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        var upload=new MockMultipartFile("file","../minta.txt","text/plain","hello diku".getBytes());
        long fileId=parse(mvc.perform(multipart("/api/admin/lessons/"+id+"/files").file(upload).session(admin).with(csrf())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/files/"+fileId+"/download").session(student)).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/lessons/"+id).session(admin).with(csrf()).contentType("application/json").content(lesson.replace("false","true"))).andExpect(status().isOk());
        mvc.perform(get("/api/files/"+fileId+"/download").session(student)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().bytes("hello diku".getBytes()));
        mvc.perform(delete("/api/admin/lessons/"+id).session(admin).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/files/"+fileId+"/download").session(student)).andExpect(status().isNotFound());
    }
    @Test void disabledUsersLoseSessionsAndLastAdminIsProtected() throws Exception {
        var admin=admin();
        String email=UUID.randomUUID()+"@example.com";
        String body=json.writeValueAsString(Map.of("name","Teszt felhasználó","email",email,"password","TestPassword2026!","role","STUDENT"));
        long id=parse(mvc.perform(post("/api/admin/users").session(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        var student=login(email,"TestPassword2026!");
        mvc.perform(patch("/api/admin/users/"+id).session(admin).with(csrf()).contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(student)).andExpect(status().isUnauthorized());
        long adminId=parse(mvc.perform(get("/api/auth/me").session(admin)).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(patch("/api/admin/users/"+adminId).session(admin).with(csrf()).contentType("application/json").content("{\"role\":\"STUDENT\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/logout").session(admin).with(csrf())).andExpect(status().isNoContent());
    }
    @Test void invalidMeetingLinksAndOversizeUtf8PasswordsAreRejected() throws Exception {
        var admin=admin();
        mvc.perform(post("/api/admin/lessons").session(admin).with(csrf()).contentType("application/json").content("{\"title\":\"Teszt\",\"meetingUrl\":\"javascript:alert(1)\",\"published\":false}"))
            .andExpect(status().isBadRequest());
        String body=json.writeValueAsString(Map.of("name","Teszt","email","long@example.com","password","á".repeat(40),"role","STUDENT"));
        mvc.perform(post("/api/admin/users").session(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
}
