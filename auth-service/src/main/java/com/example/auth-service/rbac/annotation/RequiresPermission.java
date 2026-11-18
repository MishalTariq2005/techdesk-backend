package com.example.auth_service.rbac.annotation;

import java.lang.annotation.*;

/**
 * Method-level permission gate. Checked by PermissionAspect against the
 * permissions[] claim already embedded in the caller's JWT.
 *
 * If more than one permission is listed, ANY of them being present is
 * sufficient (OR semantics) — matches how most of TechDesk's endpoints
 * are shared across roles (e.g. IT_MANAGER and COMPANY_ADMIN both hitting
 * the same reassignment endpoint).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String[] value();
}
