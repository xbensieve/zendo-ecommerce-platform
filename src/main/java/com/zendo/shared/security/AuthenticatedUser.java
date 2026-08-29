package com.zendo.shared.security;

import java.util.Collection;

public interface AuthenticatedUser {
    String getUserId();
    Collection<String> getRoles();
}
