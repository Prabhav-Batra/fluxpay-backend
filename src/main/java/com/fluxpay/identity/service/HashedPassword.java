package com.fluxpay.identity.service;

/** A bcrypt hash produced by {@link UserService#hashPassword}. Never holds the raw password. */
public record HashedPassword(String value) {

    @Override
    public String toString() {
        return "HashedPassword[redacted]";
    }
}
