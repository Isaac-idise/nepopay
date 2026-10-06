package com.nepopay.account;

import java.util.UUID;

/** Lightweight (id, status) view of an account. */
public record AccountRef(UUID id, AccountStatus status) {
}
