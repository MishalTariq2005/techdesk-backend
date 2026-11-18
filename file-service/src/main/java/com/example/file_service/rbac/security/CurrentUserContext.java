package com.example.file_service.rbac.security;

import java.util.Set;

public class CurrentUserContext {

    public record Identity(String userId, String role, Set<String> permissions) {}

    private static final ThreadLocal<Identity> HOLDER = new ThreadLocal<>();

    public static void set(Identity identity) {
        HOLDER.set(identity);
    }

    public static Identity get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
