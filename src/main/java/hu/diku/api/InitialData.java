package hu.diku.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class InitialData implements ApplicationRunner {
    private final PlatformService service;
    private final Environment env;
    private final AccountRepository users;
    private final LessonRepository lessons;
    InitialData(PlatformService service, Environment env, AccountRepository users, LessonRepository lessons) { this.service=service; this.env=env; this.users=users; this.lessons=lessons; }
    @Override @Transactional public void run(ApplicationArguments args) {
        boolean local=env.matchesProfiles("local");
        // Never reset existing accounts or recreate users removed by an operator.
        if(users.count()!=0) return;
        if(local) {
            add("Helyi admin",env.getRequiredProperty("diku.seed.admin-email"),env.getRequiredProperty("diku.seed.admin-password"),Role.ADMIN);
            add("Minta diák",env.getRequiredProperty("diku.seed.student-email"),env.getRequiredProperty("diku.seed.student-password"),Role.STUDENT);
            var lesson=new Lesson(); lesson.title="Mintaóra – ismerkedés a felülettel";
            lesson.description="Helyi bemutatóadat. Az adminfelületen szerkeszthető, tananyag csatolható hozzá, vagy törölhető.";
            lesson.published=true; lessons.save(lesson);
        } else {
            String email=env.getProperty("BOOTSTRAP_ADMIN_EMAIL","").strip();
            String password=env.getProperty("BOOTSTRAP_ADMIN_PASSWORD","");
            if(email.isEmpty() && password.isEmpty()) return;
            if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length()>254) throw new IllegalStateException("Valid BOOTSTRAP_ADMIN_EMAIL required.");
            String name=env.getProperty("BOOTSTRAP_ADMIN_NAME","DIKU admin").strip();
            if(name.isEmpty() || name.length()>120) throw new IllegalStateException("BOOTSTRAP_ADMIN_NAME must contain 1–120 characters.");
            add(name,email,password,Role.ADMIN);
        }
    }
    private void add(String name,String email,String password,Role role) {
        service.createUser(new ApiDtos.CreateUser(name,email,password,role,true),"bootstrap");
    }
}
