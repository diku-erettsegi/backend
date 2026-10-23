package hu.diku.api;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
class TimeConfiguration {
    @Bean Clock applicationClock() { return Clock.systemUTC(); }
}

@Component
class CoursePricing {
    static final ZoneId BUDAPEST=ZoneId.of("Europe/Budapest");
    private final Clock clock;
    CoursePricing(Clock clock) { this.clock=clock; }
    RegistrationDtos.PriceDto price(Course course) {
        return price(course,0,false);
    }
    RegistrationDtos.PriceDto price(Course course,int friendCount,boolean referredByFriend) {
        return priceAt(course,friendCount,referredByFriend,clock.instant());
    }
    RegistrationDtos.PriceDto priceAt(Course course,int friendCount,boolean referredByFriend,Instant createdAt) {
        return price(course.baseTotal,course.sessions*course.lessonsPerSession,createdAt.atZone(BUDAPEST).toLocalDate(),friendCount,referredByFriend);
    }
    static RegistrationDtos.PriceDto price(int baseTotal,int lessonCount,LocalDate today,int friendCount,boolean referredByFriend) {
        LocalDate first=LocalDate.of(2026,11,15), second=LocalDate.of(2026,12,15);
        int periodPercent=!today.isAfter(first)?25:!today.isAfter(second)?15:0;
        int friendPercent=5*Math.max(friendCount,referredByFriend?1:0);
        int percent=Math.min(35,periodPercent+friendPercent);
        String label=percent==0?"Teljes ár":friendPercent>0?"Összevont kedvezmény – "+percent+"%":"Korai jelentkezés – "+percent+"%";
        int total=Math.toIntExact(Math.round(baseTotal*(100-percent)/100.0));
        return new RegistrationDtos.PriceDto(baseTotal,percent,periodPercent,friendPercent,label,total,Math.toIntExact(Math.round((double)total/lessonCount)),
            periodPercent==25?first:periodPercent==15?second:null);
    }
}
