package com.ibm.securecapitaserver.service;

import com.ibm.securecapitaserver.domain.Role;

public interface RoleService {

    Role getRoleByUserId(Long id);
}
