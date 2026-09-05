package com.fundaro.zodiac.taurus.multitenancy;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TenantTransactionExecutor {

    private final TransactionTemplate transactionTemplate;

    public TenantTransactionExecutor(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void execute(String tenantCode, Runnable action) {
        TenantContext.run(
            tenantCode,
            () -> transactionTemplate.executeWithoutResult(status -> action.run())
        );
    }

    public <T> T execute(String tenantCode, Supplier<T> action) {
        return TenantContext.call(
            tenantCode,
            () -> transactionTemplate.execute(status -> action.get())
        );
    }
}
