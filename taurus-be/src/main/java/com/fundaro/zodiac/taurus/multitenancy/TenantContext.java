package com.fundaro.zodiac.taurus.multitenancy;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Holds the tenant selected for the current synchronous unit of work.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<String> getTenantCode() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static Scope use(String tenantCode) {
        String previous = CURRENT_TENANT.get();
        if (tenantCode == null || tenantCode.isBlank()) {
            CURRENT_TENANT.remove();
        } else {
            CURRENT_TENANT.set(tenantCode);
        }
        return new Scope(previous);
    }

    public static void run(String tenantCode, Runnable action) {
        try (Scope ignored = use(tenantCode)) {
            action.run();
        }
    }

    public static <T> T call(String tenantCode, Supplier<T> action) {
        try (Scope ignored = use(tenantCode)) {
            return action.get();
        }
    }

    public static final class Scope implements AutoCloseable {

        private final String previous;
        private boolean closed;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT_TENANT.remove();
            } else {
                CURRENT_TENANT.set(previous);
            }
        }
    }
}
