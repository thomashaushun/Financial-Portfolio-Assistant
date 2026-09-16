package com.tsh11.fypcode.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

public class AppUserPrincipal extends User {

    private final UUID userId;
    private final String displayName;

    public AppUserPrincipal(UUID userId,
                            String displayName,
                            String username,
                            String password,
                            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.displayName = displayName;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }
}