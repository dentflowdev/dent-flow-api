package com.dentalManagement.dentalFlowBackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @GetMapping("/getmyrole")
    public ResponseEntity<Map<String, Set<String>>> getMyRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(role -> !role.equals("ROLE_DEFAULT_USER"))
                .collect(Collectors.toSet());

        return ResponseEntity.ok(Map.of("role", roles));
    }
}
