package com.ibm.securecapitaserver.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String address;
    private String phone;
    private String title;
    private String bio;
    private String imageUrl;
    private Boolean enabled;
    private Boolean isNonLocked;
    private Boolean isUsingMfa;
    private LocalDateTime createdAt;
}
