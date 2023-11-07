package com.ibm.securecapitaserver.resource;

import com.ibm.securecapitaserver.domain.HttpResponse;
import com.ibm.securecapitaserver.domain.User;
import com.ibm.securecapitaserver.domain.UserPrincipal;
import com.ibm.securecapitaserver.dto.UserDTO;
import com.ibm.securecapitaserver.exception.ApiException;
import com.ibm.securecapitaserver.form.LoginForm;
import com.ibm.securecapitaserver.provider.TokenProvider;
import com.ibm.securecapitaserver.service.RoleService;
import com.ibm.securecapitaserver.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static com.ibm.securecapitaserver.dtomapper.UserDTOMapper.toUser;
import static com.ibm.securecapitaserver.utils.ExceptionUtils.processError;
import static java.time.LocalDateTime.now;
import static java.util.Map.of;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.unauthenticated;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath;


@RestController
@RequestMapping(path = "/user")
@RequiredArgsConstructor
public class UserResource {

    private final UserService userService;
    private final RoleService roleService;
    private final AuthenticationManager authenticationManager;
    private final TokenProvider tokenProvider;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    @PostMapping("/login")
    public ResponseEntity<HttpResponse> login(@RequestBody @Valid LoginForm loginForm) {
        Authentication authentication = authenticate(loginForm.getEmail(), loginForm.getPassword());
        UserDTO user = getAuthenticatedUser(authentication);
        return user.isUsingMfa() ? sendVerificationCode(user) : sendResponse(user);
    }

    private UserDTO getAuthenticatedUser(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).getUser();
    }

    @PostMapping("/register")
    public ResponseEntity<HttpResponse> saveUser(@RequestBody @Valid User user) {
        UserDTO userDTO = userService.createUser(user);
        return ResponseEntity.created(getUri()).body(
                HttpResponse.builder()
                        .timeStamp(now().toString())
                        .data(of("user", userDTO))
                        .message("User created")
                        .status(CREATED)
                        .statusCode(CREATED.value())
                        .build()
        );
    }

    @GetMapping("/profile")
    public ResponseEntity<HttpResponse> profile(Authentication authentication) {
        UserDTO user = userService.getUserByEmail(authentication.getName());
        System.out.println(authentication);
        return ResponseEntity.ok().body(
                HttpResponse.builder()
                        .timeStamp(now().toString())
                        .data(of(
                                "user", user))
                        .message("Profile retrieved")
                        .status(OK)
                        .statusCode(OK.value())
                        .build());
    }

    @GetMapping("/verify/code/{email}/{code}")
    public ResponseEntity<HttpResponse> verifyCode(@PathVariable("email") String email, @PathVariable("code") String code) {
        UserDTO user = userService.verifyCode(email, code);
        return ResponseEntity.ok().body(
                HttpResponse.builder()
                        .timeStamp(now().toString())
                        .data(of(
                                "user", user,
                                "access_token", tokenProvider.createAccessToken(getUserPrincipal(user)),
                                "refresh_token", tokenProvider.createRefreshToken(getUserPrincipal(user))))

                        .message("Login Success")
                        .status(OK)
                        .statusCode(OK.value())
                        .build());
    }

    @RequestMapping("/error")
    public ResponseEntity<HttpResponse> handleError(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                HttpResponse.builder()
                        .timeStamp(now().toString())
                        .reason("No mapping for " + request.getMethod() + " request for this path on the server")
                        .status(BAD_REQUEST)
                        .statusCode(BAD_REQUEST.value())
                        .build());
    }

    private URI getUri() {
        return URI.create(fromCurrentContextPath().path("/user/get/<userId>").toUriString());
    }

    private ResponseEntity<HttpResponse> sendResponse(UserDTO user) {
        return ResponseEntity.ok().body(
                HttpResponse.builder()
                        .timeStamp(now().toString())
                        .data(of(
                                "user", user,
                                "access_token", tokenProvider.createAccessToken(getUserPrincipal(user)),
                                "refresh_token", tokenProvider.createRefreshToken(getUserPrincipal(user))))

                        .message("Login Success")
                        .status(OK)
                        .statusCode(OK.value())
                        .build());
    }

    private UserPrincipal getUserPrincipal(UserDTO user) {
        return new UserPrincipal(toUser(userService.getUserByEmail(user.getEmail())), roleService.getRoleByUserId(user.getId()));
    }

    private ResponseEntity<HttpResponse> sendVerificationCode(UserDTO user) {
        userService.sendVerificationCode(user);
        return ResponseEntity.ok().body(
                HttpResponse.builder()
                        .timeStamp(now().toString())
                        .data(of("user", user))
                        .message("Verification code sent")
                        .status(OK)
                        .statusCode(OK.value())
                        .build());
    }

    private Authentication authenticate(String email, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(unauthenticated(email, password));
            return authentication;
        } catch (Exception exception) {
            processError(request, response, exception);
            throw new ApiException(exception.getMessage());
        }
    }
}
