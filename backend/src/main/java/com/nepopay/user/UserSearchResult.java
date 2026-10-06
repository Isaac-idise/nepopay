package com.nepopay.user;

/** The ONLY user data other people may see: no email, phone, full account number or internal ids. */
public record UserSearchResult(String username, String displayName, String accountLast4) {

    /** Used by the JPQL constructor expression in UserRepository.search. */
    public UserSearchResult(String username, String firstName, String lastName, String accountNumber) {
        this(username, firstName + " " + lastName, accountNumber.substring(accountNumber.length() - 4));
    }
}
