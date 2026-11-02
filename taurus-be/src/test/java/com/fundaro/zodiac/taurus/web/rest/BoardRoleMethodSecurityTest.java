package com.fundaro.zodiac.taurus.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * Only one role reaches the token at a time, so a resource open to the administrator has to name the board too. The
 * calendar feeds of every member stay with the administrators (docs/board-role-spec.md).
 */
class BoardRoleMethodSecurityTest {

    private static final Set<Class<?>> ADMINISTRATOR_ONLY = Set.of(AdminCalendarFeedResource.class);

    @Test
    void opensToTheBoardWhatIsOpenToTheAdministrator() throws ClassNotFoundException {
        List<String> missing = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        AnnotationTypeFilter restController = new AnnotationTypeFilter(RestController.class);
        // Every resource counts, also those switched on by a property: their conditions are not evaluated here.
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(MetadataReader reader) throws IOException {
                return restController.match(reader, getMetadataReaderFactory());
            }
        };
        for (BeanDefinition definition : scanner.findCandidateComponents(getClass().getPackageName())) {
            Class<?> resource = Class.forName(definition.getBeanClassName());
            if (ADMINISTRATOR_ONLY.contains(resource)) continue;
            check(resource, resource.getSimpleName(), missing, checked);
            for (var method : resource.getDeclaredMethods()) check(method, resource.getSimpleName() + "." + method.getName(), missing, checked);
        }

        assertThat(checked).isNotEmpty();
        assertThat(missing).isEmpty();
    }

    @Test
    void keepsTheCalendarFeedsOfEveryMemberToTheAdministrators() {
        PreAuthorize rule = AdminCalendarFeedResource.class.getAnnotation(PreAuthorize.class);

        assertThat(rule.value()).contains("ROLE_ADMIN").doesNotContain("ROLE_BOARD");
    }

    private static void check(AnnotatedElement element, String name, List<String> missing, List<String> checked) {
        PreAuthorize rule = element.getAnnotation(PreAuthorize.class);
        if (rule == null || !rule.value().contains("'ROLE_ADMIN'")) return;
        checked.add(name);
        if (!rule.value().contains("'ROLE_BOARD'")) missing.add(name + ": " + rule.value());
    }
}
