package com.no8do.api.auth;

import com.no8do.api.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class No8doUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public No8doUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(normalizeEmail(email))
            .map(No8doUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
