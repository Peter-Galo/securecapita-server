package com.ibm.securecapitaserver.service.implementation;

import com.ibm.securecapitaserver.domain.Role;
import com.ibm.securecapitaserver.domain.User;
import com.ibm.securecapitaserver.dto.UserDTO;
import com.ibm.securecapitaserver.repository.RoleRepository;
import com.ibm.securecapitaserver.repository.UserRepository;
import com.ibm.securecapitaserver.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.ibm.securecapitaserver.dtomapper.UserDTOMapper.fromUser;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository<User> userRepository;
    private final RoleRepository<Role> roleRepository;

    @Override
    public UserDTO createUser(User user) {
        return mapToUserDto(userRepository.create(user));
    }

    @Override
    public UserDTO getUserByEmail(String email) {
        return mapToUserDto(userRepository.getUserByEmail(email));
    }

    @Override
    public void sendVerificationCode(UserDTO user) {
        userRepository.sendVerificationCode(user);
    }

    @Override
    public UserDTO verifyCode(String email, String code) {
        return mapToUserDto(userRepository.verifyCode(email, code));
    }

    private UserDTO mapToUserDto(User user) {
        return fromUser(user, roleRepository.getRoleByUserId(user.getId()));
    }
}
