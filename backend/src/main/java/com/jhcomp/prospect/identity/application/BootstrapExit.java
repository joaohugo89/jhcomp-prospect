package com.jhcomp.prospect.identity.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ends the process after {@link AdminBootstrapCommand} (D27).
 *
 * <p>Kept separate from the command so that the command can be tested without
 * tearing down the JVM. It runs last; if the command failed, the context never
 * finishes starting and the process exits with an error before reaching
 * here.
 */
@Component
@Profile("bootstrap")
@Order(Ordered.LOWEST_PRECEDENCE)
class BootstrapExit implements ApplicationRunner {

    private final ApplicationContext context;

    BootstrapExit(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
