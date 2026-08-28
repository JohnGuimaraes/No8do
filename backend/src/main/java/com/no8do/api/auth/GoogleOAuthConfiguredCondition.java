package com.no8do.api.auth;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GoogleOAuthConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String clientId = context.getEnvironment().getProperty("no8do.google.client-id", "");
        String clientSecret = context.getEnvironment().getProperty("no8do.google.client-secret", "");
        return !clientId.isBlank() && !clientSecret.isBlank();
    }
}
