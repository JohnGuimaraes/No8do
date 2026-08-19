package com.no8do.api.client;

public record ClientRequest(
        String name,
        String companyName,
        String email,
        String phone,
        String notes
) {
}
