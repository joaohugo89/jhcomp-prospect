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
 * Encerra o processo depois do {@link AdminBootstrapCommand} (D27).
 *
 * <p>Separado do comando para que ele possa ser testado sem derrubar a JVM.
 * Roda por ultimo; se o comando falhou, o contexto nem termina de subir e o
 * processo sai com erro antes de chegar aqui.
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
