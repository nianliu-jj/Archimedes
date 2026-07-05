package io.github.nianliu.archimedes.trace;

import java.util.UUID;

public class UuidTraceIdGenerator implements TraceIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
