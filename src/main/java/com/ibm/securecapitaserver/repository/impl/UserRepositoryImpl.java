package com.ibm.securecapitaserver.repository.impl;

import com.ibm.securecapitaserver.domain.Role;
import com.ibm.securecapitaserver.domain.User;
import com.ibm.securecapitaserver.domain.UserPrincipal;
import com.ibm.securecapitaserver.dto.UserDTO;
import com.ibm.securecapitaserver.enumeration.VerificationType;
import com.ibm.securecapitaserver.exception.ApiException;
import com.ibm.securecapitaserver.repository.RoleRepository;
import com.ibm.securecapitaserver.repository.UserRepository;
import com.ibm.securecapitaserver.rowmapper.UserRowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collection;
import java.util.Date;
import java.util.UUID;

import static com.ibm.securecapitaserver.enumeration.RoleType.ROLE_USER;
import static com.ibm.securecapitaserver.enumeration.VerificationType.ACCOUNT;
import static com.ibm.securecapitaserver.enumeration.VerificationType.PASSWORD;
import static com.ibm.securecapitaserver.query.UserQuery.*;
import static com.ibm.securecapitaserver.utils.SmsUtils.sendSms;
import static java.util.Map.of;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.time.DateFormatUtils.format;
import static org.apache.commons.lang3.time.DateUtils.addDays;

@Repository
@RequiredArgsConstructor
@Slf4j
public class UserRepositoryImpl implements UserRepository<User>, UserDetailsService {


    private static final String DATE_FORMAT = "yyyy-MM-dd hh:mm:ss";
    private final NamedParameterJdbcTemplate jdbc;
    private final RoleRepository<Role> roleRepository;
    private final BCryptPasswordEncoder encoder;
    private final Environment environment;

    @Override
    public User create(User user) {
        if (getEmailCount(user.getEmail().trim().toLowerCase()) > 0) {
            throw new ApiException("Email already in use. Please use a different email and try again.");
        }
        try {
            KeyHolder holder = new GeneratedKeyHolder();
            SqlParameterSource parameters = getSqlParameterSource(user);
            jdbc.update(INSERT_USER_QUERY, parameters, holder);
            user.setId(requireNonNull(holder.getKey()).longValue());

            roleRepository.addRoleToUser(user.getId(), ROLE_USER.name());

            String verificationUrl = getVerificationUrl(UUID.randomUUID().toString(), ACCOUNT.getType());
            jdbc.update(INSERT_ACCOUNT_VERIFICATION_URL_QUERY, of("userId", user.getId(), "url", verificationUrl));
//            emailService.sendVerificationUrl(user.getFirstName(), user.getEmail(), verificationUrl, ACCOUNT);
            user.setEnabled(false);
            user.setNotLocked(true);
            return user;
        } catch (Exception exception) {
            throw new ApiException("An error occurred. Please try again.");
        }
    }


    @Override
    public Collection<User> list(int page, int pageSize) {
        return null;
    }

    @Override
    public User get(Long id) {
        return null;
    }

    @Override
    public User update(User data) {
        return null;
    }

    @Override
    public Boolean delete(Long id) {
        return null;
    }

    private Integer getEmailCount(String email) {
        return jdbc.queryForObject(COUNT_USER_EMAIL_QUERY, of("email", email), Integer.class);
    }

    private SqlParameterSource getSqlParameterSource(User user) {
        return new MapSqlParameterSource()
                .addValue("firstName", user.getFirstName())
                .addValue("lastName", user.getLastName())
                .addValue("email", user.getEmail())
                .addValue("password", encoder.encode(user.getPassword()));
    }

    private String getVerificationUrl(String key, String type) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/user/verify/" + type + "/" + key).toUriString();
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        User user = getUserByEmail(email);
        if (user == null) {
            log.error("User [{}] not found in database", email);
            throw new UsernameNotFoundException("User " + email + " not found in database");
        } else {
            log.info("User [{}] found in database", email);
            return new UserPrincipal(user, roleRepository.getRoleByUserId(user.getId()));
        }
    }

    @Override
    public User getUserByEmail(String email) {
        System.out.println(email);
        try {
            User user = jdbc.queryForObject(SELECT_USER_BY_EMAIL_QUERY, of("email", email), new UserRowMapper());
            return user;
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException("No User found by email: " + email);
        } catch (Exception exception) {
            log.error(exception.getMessage());
            throw new ApiException("An error occurred. Please try again.");
        }
    }

    @Override
    public void sendVerificationCode(UserDTO user) {
        String expirationDate = format(addDays(new Date(), 1), DATE_FORMAT);
        String verificationCode = randomAlphabetic(8).toUpperCase();

        try {
            jdbc.update(DELETE_VERIFICATION_CODE_BY_USER_ID_QUERY, of("id", user.getId()));
            jdbc.update(INSERT_VERIFICATION_CODE_QUERY, of(
                    "userId", user.getId(),
                    "code", verificationCode,
                    "expirationDate", expirationDate));

            String fromNumber = environment.getProperty("twilio.from_number");
            String sidKey = environment.getProperty("twilio.sid_key");
            String tokenKey = environment.getProperty("twilio.token_key");

            sendSms(user.getPhone(), fromNumber, "From SecureCapita\nVerification Code\n" + verificationCode, sidKey, tokenKey);
        } catch (Exception exception) {
            log.error(exception.getMessage());
            throw new ApiException("An error occurred. Please try again.");
        }
    }

    @Override
    public User verifyCode(String email, String code) {
        if (isVerificationExpired(code)) {
            throw new ApiException("This code has expired. Please log in again");
        }

        try {
            User userByCode = jdbc.queryForObject(SELECT_USER_BY_USER_CODE_QUERY, of("code", code), new UserRowMapper());
            User userByEmail = jdbc.queryForObject(SELECT_USER_BY_EMAIL_QUERY, of("email", email), new UserRowMapper());

            if (userByCode.getEmail().equalsIgnoreCase(userByEmail.getEmail())) {
                jdbc.update(DELETE_VERIFICATION_CODE_BY_USER_ID_QUERY, of("id", userByCode.getId()));
                return userByCode;
            } else {
                throw new ApiException("Code is invalid. Please try again.");
            }

        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException("Could not find a record.");
        } catch (Exception exception) {
            throw new ApiException("An exception occured");
        }
    }

    @Override
    public void resetPassword(String email) {
        if (getEmailCount(email.trim().toLowerCase()) == 0) {
            throw new ApiException("There is no account for this email address: " + email);
        }
        try {
            String expirationDate = format(addDays(new Date(), 1), DATE_FORMAT);
            User user = getUserByEmail(email);
            String verificationUrl = getVerificationUrl(UUID.randomUUID().toString(), PASSWORD.getType());

            log.info(verificationUrl);

            jdbc.update(DELETE_PASSWORD_VERIFICATION_BY_USER_ID_QUERY, of("userId", user.getId()));
            jdbc.update(INSERT_PASSWORD_VERIFICATION_QUERY, of(
                    "userId", user.getId(),
                    "url", verificationUrl,
                    "expirationDate", expirationDate));
            // TODO send email url to user
        } catch (Exception exception) {
            throw new ApiException("An exception occurred");
        }
    }

    @Override
    public User verifyPasswordKey(String key) {
        if (isLinkExpired(key, PASSWORD)) {
            throw new ApiException("This link has expired. Please reset your root password again");
        }

        try {
            User user = jdbc.queryForObject(SELECT_USER_BY_PASSWORD_URL_QUERY, of("url", getVerificationUrl(key, PASSWORD.getType())), new UserRowMapper());
            // jdbc.update(DELETE_USER_FROM_PASSWORD_VERIFICATION_QUERY, of("userId", user.getId()));
            return user;
        } catch (EmptyResultDataAccessException exception) {
            log.info((exception.getMessage()));
            throw new ApiException("This link is not valid. Please reset your password again.");
        } catch (Exception exception) {
            log.info((exception.getMessage()));
            throw new ApiException("An exception occured");
        }
    }

    @Override
    public void renewPassword(String key, String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new ApiException("Passwords don't match.");
        }
        try {
            jdbc.update(UPDATE_USER_PASSWORD_BY_URL_QUERY, of("password", encoder.encode(password), "url", getVerificationUrl(key, PASSWORD.getType())));
            jdbc.update(DELETE_VERIFICATION_BY_URL_QUERY, of("url", getVerificationUrl(key, PASSWORD.getType())));
        } catch (Exception exception) {
            throw new ApiException("An exception occurred.");
        }

    }

    @Override
    public User verifyAccountKey(String key) {
        try {
            User user = jdbc.queryForObject(SELECT_USER_BY_ACCOUNT_URL_QUERY, of("url", getVerificationUrl(key, ACCOUNT.getType())), new UserRowMapper());
            jdbc.update(UPDATE_USER_ENABLED_QUERY, of("enabled", true, "userId", user.getId()));
            // delete account verification url after verification - optional, depends on the use case
            return user;
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException("This link is not valid.");
        } catch (Exception exception) {
            throw new ApiException("An exception occured");
        }
    }

    private Boolean isLinkExpired(String key, VerificationType password) {
        try {
            return jdbc.queryForObject(SELECT_EXPIRATION_BY_URL_QUERY, of("url", getVerificationUrl(key, password.getType())), Boolean.class);
        } catch (EmptyResultDataAccessException exception) {
            log.info((exception.getMessage()));
            throw new ApiException("This link is not valid. Please reset your password again.");
        } catch (Exception exception) {
            log.info((exception.getMessage()));
            throw new ApiException("An exception occured");
        }
    }

    private Boolean isVerificationExpired(String code) {
        try {
            return jdbc.queryForObject(SELECT_CODE_EXPIRATION_QUERY, of("code", code), Boolean.class);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException("This code is not valid. Please log in again.");
        } catch (Exception exception) {
            throw new ApiException("An exception occured");
        }
    }
}
