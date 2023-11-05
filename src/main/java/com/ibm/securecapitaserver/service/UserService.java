package com.ibm.securecapitaserver.service;

import com.ibm.securecapitaserver.domain.User;
import com.ibm.securecapitaserver.dto.UserDTO;

public interface UserService {
    UserDTO createUser(User user);

    UserDTO getUserByEmail(String email);
}
