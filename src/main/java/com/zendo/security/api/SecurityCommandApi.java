package com.zendo.security.api;

public interface SecurityCommandApi {
    void upgradeToVendorIfCustomer(String userId);
    void updateUserRole(String userId, String newRole);
}
