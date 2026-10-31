package com.jhcomp.prospect.outreach;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import com.jhcomp.prospect.outreach.domain.AIProvider;
import com.jhcomp.prospect.outreach.domain.AIResponse;
import com.jhcomp.prospect.outreach.domain.MessageGenerationRequest;

/**
 * A IA nao tem caminho de escrita (regra 1 do produto, doc 06 §11).
 *
 * <p>Confere a interface e cada implementacao encontrada no classpath: o que
 * um provedor recebe por construtor ou guarda em campo define o que ele pode
 * fazer. Repositorio, servico de aplicacao, acesso a banco ou qualquer classe
 * do sistema fora do dominio de outreach daria a IA um meio de gravar,
 * enviar ou remover bloqueio.
 */
@DisplayName("Estrutura: provedor de IA sem ferramenta de escrita")
class AIProviderStructureTest {

    private static final String BASE_PACKAGE = "com.jhcomp.prospect";
    private static final String ALLOWED_INTERNAL_PACKAGE = "com.jhcomp.prospect.outreach.domain";

    /** Pacotes externos que dao acesso a dados ou a efeito colateral do sistema. */
    private static final List<String> FORBIDDEN_EXTERNAL = List.of(
            "org.springframework.data", "org.springframework.jdbc", "org.springframework.orm",
            "org.springframework.transaction", "org.springframework.context.ApplicationEventPublisher",
            "jakarta.persistence", "javax.sql", "org.hibernate", "jakarta.mail", "org.springframework.mail");

    @Test
    @DisplayName("a interface tem um unico metodo, que so devolve texto")
    void interfaceExposesOnlyGeneration() {
        Method[] methods = AIProvider.class.getDeclaredMethods();

        assertThat(methods).hasSize(1);
        assertThat(methods[0].getName()).isEqualTo("generateMessage");
        assertThat(methods[0].getReturnType()).isEqualTo(AIResponse.class);
        assertThat(methods[0].getParameterTypes()).containsExactly(MessageGenerationRequest.class);
    }

    @Test
    @DisplayName("nenhuma implementacao recebe ou guarda dependencia que grave, envie ou consulte o banco")
    void implementationsHaveNoWriteDependencies() throws Exception {
        List<Class<?>> implementations = implementations();
        assertThat(implementations).as("ao menos o provedor fake").isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> implementation : implementations) {
            for (Field field : implementation.getDeclaredFields()) {
                check(implementation, "field " + field.getName(), field.getType(), violations);
            }
            for (Constructor<?> constructor : implementation.getDeclaredConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    check(implementation, "constructor parameter", parameter, violations);
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    private static void check(Class<?> owner, String where, Class<?> type, List<String> violations) {
        Class<?> checked = type.isArray() ? type.getComponentType() : type;
        String name = checked.getName();
        boolean internal = name.startsWith(BASE_PACKAGE + ".");
        boolean allowedInternal = name.startsWith(ALLOWED_INTERNAL_PACKAGE + ".") || checked.getEnclosingClass() == owner
                || checked == owner;
        if (internal && !allowedInternal) {
            violations.add(owner.getSimpleName() + " " + where + ": " + name);
        }
        if (FORBIDDEN_EXTERNAL.stream().anyMatch(name::startsWith)) {
            violations.add(owner.getSimpleName() + " " + where + ": " + name);
        }
    }

    private static List<Class<?>> implementations() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(AIProvider.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            classes.add(Class.forName(candidate.getBeanClassName()));
        }
        return classes;
    }
}
