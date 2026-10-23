package hu.diku.api;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoursePricingTest {
    @ParameterizedTest
    @CsvSource({
        "2026-11-15T22:59:59Z,0,false,25,0,25,210000,2625",
        "2026-11-15T23:00:00Z,0,false,15,0,15,238000,2975",
        "2026-12-15T22:59:59Z,0,false,15,0,15,238000,2975",
        "2026-12-15T23:00:00Z,0,false,0,0,0,280000,3500",
        "2026-11-01T12:00:00Z,1,false,25,5,30,196000,2450",
        "2026-11-01T12:00:00Z,0,true,25,5,30,196000,2450",
        "2026-11-01T12:00:00Z,1,true,25,5,30,196000,2450",
        "2026-11-01T12:00:00Z,2,false,25,10,35,182000,2275",
        "2026-12-01T12:00:00Z,1,false,15,5,20,224000,2800",
        "2026-12-16T12:00:00Z,1,false,0,5,5,266000,3325",
        "2026-12-16T12:00:00Z,10,true,0,50,35,182000,2275"
    })
    void quoteUsesBudapestDeadlineAndStackedCap(String instant,int friends,boolean referred,int period,int friendPercent,int totalPercent,int total,int perLesson) {
        var course=new Course(); course.baseTotal=280000; course.sessions=20; course.lessonsPerSession=4;
        var pricing=new CoursePricing(Clock.fixed(Instant.parse(instant),ZoneOffset.UTC));
        var quote=pricing.price(course,friends,referred);
        assertThat(quote.periodDiscountPercent()).isEqualTo(period);
        assertThat(quote.friendDiscountPercent()).isEqualTo(friendPercent);
        assertThat(quote.discountPercent()).isEqualTo(totalPercent);
        assertThat(quote.total()).isEqualTo(total); assertThat(quote.perLesson()).isEqualTo(perLesson);
    }
}
