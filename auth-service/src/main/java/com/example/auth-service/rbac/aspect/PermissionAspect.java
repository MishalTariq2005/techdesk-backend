package com.example.auth_service.rbac.aspect;

import com.example.auth_service.rbac.annotation.RequiresPermission;
import com.example.auth_service.rbac.exception.InsufficientPermissionException;
import com.example.auth_service.rbac.security.CurrentUserContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.JoinPoint;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * RBAC layer 2 of 3 (see roadmap 4.1 challenge). Layer 1 is the gateway
 * (GatewayRoleCheckFilter, coarse role-claim check). Layer 3 is Postgres RLS
 * (tenant/V3 migration). This layer checks the fine-grained permissions[]
 * claim against @RequiresPermission on the method being called.
 *
 * Requires spring-boot-starter-aop on the classpath (not yet in auth-service's
 * pom.xml as of Week 1 — add it before this compiles).
 */
@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(requiresPermission)")
    public void checkPermission(JoinPoint joinPoint, RequiresPermission requiresPermission) {
        CurrentUserContext.Identity identity = CurrentUserContext.get();

        if (identity == null) {
            throw new InsufficientPermissionException("No authenticated identity on this request");
        }

        Set<String> required = Set.of(requiresPermission.value());
        boolean hasAny = required.stream().anyMatch(identity.permissions()::contains);

        if (!hasAny) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            throw new InsufficientPermissionException(
                "User " + identity.userId() + " (role=" + identity.role() + ") lacks required permission(s) "
                    + Arrays.toString(requiresPermission.value())
                    + " for " + signature.getMethod().getName()
            );
        }
    }
}
