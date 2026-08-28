package com.no8do.api.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserExternalIdentityRepository extends JpaRepository<UserExternalIdentity, UUID> {

    @Query("""
        select identity
        from UserExternalIdentity identity
        join fetch identity.user
        where identity.provider = :provider and identity.providerSubject = :providerSubject
        """)
    Optional<UserExternalIdentity> findByProviderAndProviderSubject(
        ExternalIdentityProvider provider,
        String providerSubject
    );
}
