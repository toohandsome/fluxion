package io.github.fluxion.test.harness.adapter.persistence;

import io.github.fluxion.persistence.harness.api.HarnessPersistencePort;
import java.util.UUID;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class SpringHarnessPersistencePortProvider implements AutoCloseable {

    private final ConfigurableApplicationContext context;

    public SpringHarnessPersistencePortProvider() {
        this.context = new SpringApplicationBuilder(HarnessPersistenceTestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=jdbc:h2:mem:fluxion_harness_context_" + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "fluxion.harness.persistence.enabled=true",
                        "fluxion.harness.persistence.initialize-schema=true"
                )
                .run();
    }

    public HarnessPersistencePort getObject() {
        return context.getBean(HarnessPersistencePort.class);
    }

    @Override
    public void close() {
        context.close();
    }

    @SpringBootApplication
    static class HarnessPersistenceTestApplication {
    }
}
