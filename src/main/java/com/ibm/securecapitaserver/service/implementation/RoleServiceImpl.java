package com.ibm.securecapitaserver.service.implementation;

import com.ibm.securecapitaserver.domain.Role;
import com.ibm.securecapitaserver.repository.RoleRepository;
import com.ibm.securecapitaserver.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository<Role> roleRepository;

    @Override
    public Role getRoleByUserId(Long id) {
        return roleRepository.getRoleByUserId(id);
    }
}
