package com.hayden.persistence.trigger;

import com.hayden.utilitymodule.otel.DisableOtelConfiguration;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("testjpa")
@Import(DisableOtelConfiguration.class)
public class DataSourceTriggerTest {


    @Autowired
    private TestBeanTwo two;
    @Autowired
    private TestBean one;


    private static final ExecutorService FIXED = Executors.newFixedThreadPool(5);
    private static final ExecutorService VIRTUAL = Executors.newVirtualThreadPerTaskExecutor();

    @SpringBootApplication(   exclude = org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration.class)
    @ComponentScan("com.hayden.persistence")
    @Import(DisableOtelConfiguration.class)
    public static class TestDbSourceApplication {
        static void main(String[] args) {
            SpringApplication.run(TestDbSourceApplication.class, args);
        }
    }

    private static final CountDownLatch countDownLatch = new CountDownLatch(4);

    @SneakyThrows
    @Test
    public void doFind() {
        one.test();

        notAsync();
        withoutExecutorService();
        withExecutor(VIRTUAL);
        withExecutor(FIXED);

        countDownLatch.await();
    }

    private void withExecutor(ExecutorService virtual) {
        AtomicInteger count = new AtomicInteger(0);
        var a = IntStream.range(0, 100)
                .boxed()
                .map(o -> {
                    count.set(o);
                    return CompletableFuture.runAsync(() -> {
                        one.test();
                    }, virtual).exceptionally(handleException());
                })
                .toArray(CompletableFuture[]::new);

        countDownAfter(a, count);
    }

    private static void countDownAfter(CompletableFuture[] a, AtomicInteger count) {
        CompletableFuture.allOf(a)
                        .thenAccept(c -> {
                            assertThat(count.get()).isEqualTo(99);
                            countDownLatch.countDown();
                        })
                .exceptionally(handleException());
    }

    private void withoutExecutorService() {
        AtomicInteger count = new AtomicInteger(0);
        var a = IntStream.range(0, 100)
                .boxed()
                .map(o -> {
                    count.set(o);
                    return CompletableFuture.runAsync(() -> {
                                one.test();
                            })
                            .exceptionally(handleException());
                })
                .toArray(CompletableFuture[]::new);
        countDownAfter(a, count);
    }

    private static @NotNull Function<Throwable, Void> handleException() {
        return tr -> {
            throw new RuntimeException(tr);
        };
    }

    private void notAsync() {
        IntStream.range(0, 100)
                .forEach(o -> one.test());
        countDownLatch.countDown();
    }


}