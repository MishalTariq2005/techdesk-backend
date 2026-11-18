package com.example.ticket_service.rbac.aspect;

import com.example.ticket_service.rbac.annotation.RequiresPermission;
import com.example.ticket_service.rbac.exception.InsufficientPermissionException;
import com.example.ticket_service.rbac.security.CurrentUserContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

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
