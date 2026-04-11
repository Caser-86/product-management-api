package com.example.ecommerce.pricing.application;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.task.scheduling.enabled=false",
    "pricing.schedule.lock-at-least-for=PT0S",
    "pricing.schedule.lock-at-most-for=PT5S"
})
class PriceScheduleRunnerLockTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private LockProvider lockProvider;

    @Autowired
    private PriceScheduleRunner priceScheduleRunner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanLocks() {
        jdbcTemplate.update("delete from shedlock");
    }

    @Test
    void creates_lock_provider_bean() {
        assertThat(applicationContext.containsBean("lockProvider")).isTrue();
    }

    @Test
    void creates_jdbc_lock_provider() {
        assertThat(lockProvider).isNotNull();
    }

    @Test
    void runner_is_exposed_as_aop_proxy() {
        assertThat(priceScheduleRunner).isNotNull();
        assertThat(AopUtils.isAopProxy(priceScheduleRunner)).isTrue();
        assertThat(Arrays.stream(PriceScheduleRunner.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("runDueSchedules"))
            .findFirst()
            .orElseThrow()
            .isAnnotationPresent(SchedulerLock.class)).isTrue();
    }
}
