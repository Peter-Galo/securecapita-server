package com.ibm.securecapitaserver.service.implementation;

import com.ibm.securecapitaserver.domain.User;
import com.ibm.securecapitaserver.dto.UserDTO;
import com.ibm.securecapitaserver.dtomapper.UserDTOMapper;
import com.ibm.securecapitaserver.repository.UserRepository;
import com.ibm.securecapitaserver.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository<User> userRepository;
    @Override
    public UserDTO createUser(User user) {
        return UserDTOMapper.fromUser(userRepository.create(user));
    }

    @Override
    public UserDTO getUserByEmail(String email) {
        return UserDTOMapper.fromUser(userRepository.getUserByEmail(email));
    }
}
