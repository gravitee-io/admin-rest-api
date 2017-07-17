/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.management.service;

import io.gravitee.management.model.NewExternalUserEntity;
import io.gravitee.management.model.RoleEntity;
import io.gravitee.management.model.UserRoleEntity;
import io.gravitee.repository.management.model.MembershipDefaultReferenceId;
import io.gravitee.repository.management.model.MembershipReferenceType;
import io.gravitee.repository.management.model.RoleScope;
import io.gravitee.management.model.UserEntity;
import io.gravitee.management.service.exceptions.TechnicalManagementException;
import io.gravitee.management.service.exceptions.UserNotFoundException;
import io.gravitee.management.service.exceptions.UsernameAlreadyExistsException;
import io.gravitee.management.service.impl.UserServiceImpl;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.UserRepository;
import io.gravitee.repository.management.model.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Azize Elamrani (azize dot elamrani at gmail dot com)
 */
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    private static final String USER_NAME = "tuser";
    private static final String EMAIL = "user@gravitee.io";
    private static final String FIRST_NAME = "The";
    private static final String LAST_NAME = "User";
    private static final String PASSWORD = "gh2gyf8!zjfnz";
    private static final Set<UserRoleEntity> ROLES = Collections.singleton(new UserRoleEntity());
    static {
        UserRoleEntity r = ROLES.iterator().next();
        r.setScope(io.gravitee.management.model.permissions.RoleScope.PORTAL);
        r.setName("USER");
    }

    @InjectMocks
    private UserServiceImpl userService = new UserServiceImpl();

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private RoleService roleService;

    @Mock MembershipService membershipService;

    @Mock
    private NewExternalUserEntity newUser;
    @Mock
    private User user;
    @Mock
    private Date date;

    @Test
    public void shouldFindByUsername() throws TechnicalException {
        when(user.getUsername()).thenReturn(USER_NAME);
        when(user.getEmail()).thenReturn(EMAIL);
        when(user.getFirstname()).thenReturn(FIRST_NAME);
        when(user.getLastname()).thenReturn(LAST_NAME);
        when(user.getPassword()).thenReturn(PASSWORD);
        when(userRepository.findByUsernames(Collections.singletonList(USER_NAME))).thenReturn(Collections.singleton(user));

        final UserEntity userEntity = userService.findByName(USER_NAME, false);

        assertEquals(USER_NAME, userEntity.getUsername());
        assertEquals(FIRST_NAME, userEntity.getFirstname());
        assertEquals(LAST_NAME, userEntity.getLastname());
        assertEquals(EMAIL, userEntity.getEmail());
        assertEquals(PASSWORD, userEntity.getPassword());
        assertEquals(null, userEntity.getRoles());
    }

    @Test(expected = UserNotFoundException.class)
    public void shouldNotFindByUsernameBecauseNotExists() throws TechnicalException {
        when(userRepository.findByUsername(USER_NAME)).thenReturn(Optional.empty());

        userService.findByName(USER_NAME, false);
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotFindByUsernameBecauseTechnicalException() throws TechnicalException {
        when(userRepository.findByUsernames(Collections.singletonList(USER_NAME))).thenThrow(TechnicalException.class);

        userService.findByName(USER_NAME, false);
    }

    @Test
    public void shouldCreate() throws TechnicalException {
        when(newUser.getUsername()).thenReturn(USER_NAME);
        when(newUser.getEmail()).thenReturn(EMAIL);
        when(newUser.getFirstname()).thenReturn(FIRST_NAME);
        when(newUser.getLastname()).thenReturn(LAST_NAME);

        when(userRepository.findByUsername(USER_NAME)).thenReturn(Optional.empty());

        when(user.getUsername()).thenReturn(USER_NAME);
        when(user.getEmail()).thenReturn(EMAIL);
        when(user.getFirstname()).thenReturn(FIRST_NAME);
        when(user.getLastname()).thenReturn(LAST_NAME);
        when(user.getPassword()).thenReturn(PASSWORD);
        when(user.getCreatedAt()).thenReturn(date);
        when(user.getUpdatedAt()).thenReturn(date);
        when(userRepository.create(any(User.class))).thenReturn(user);
        RoleEntity role = mock(RoleEntity.class);
        when(role.getScope()).thenReturn(io.gravitee.management.model.permissions.RoleScope.PORTAL);
        when(role.getName()).thenReturn("USER");
        when(roleService.findDefaultRoleByScopes(RoleScope.MANAGEMENT, RoleScope.PORTAL)).thenReturn(Collections.singletonList(role));
        when(membershipService.getRole(
                MembershipReferenceType.PORTAL,
                MembershipDefaultReferenceId.DEFAULT.name(),
                user.getUsername())).thenReturn(role);

        final UserEntity createdUserEntity = userService.create(newUser, false);

        verify(userRepository).create(argThat(new ArgumentMatcher<User>() {
            public boolean matches(final Object argument) {
                final User userToCreate = (User) argument;
                return USER_NAME.equals(userToCreate.getUsername()) &&
                    EMAIL.equals(userToCreate.getEmail()) &&
                    FIRST_NAME.equals(userToCreate.getFirstname()) &&
                    LAST_NAME.equals(userToCreate.getLastname()) &&
                    userToCreate.getCreatedAt() != null &&
                    userToCreate.getUpdatedAt() != null &&
                    userToCreate.getCreatedAt().equals(userToCreate.getUpdatedAt());
            }
        }));

        assertEquals(USER_NAME, createdUserEntity.getUsername());
        assertEquals(FIRST_NAME, createdUserEntity.getFirstname());
        assertEquals(LAST_NAME, createdUserEntity.getLastname());
        assertEquals(EMAIL, createdUserEntity.getEmail());
        assertEquals(PASSWORD, createdUserEntity.getPassword());
        assertEquals(ROLES, createdUserEntity.getRoles());
        assertEquals(date, createdUserEntity.getCreatedAt());
        assertEquals(date, createdUserEntity.getUpdatedAt());
    }

    @Test(expected = UsernameAlreadyExistsException.class)
    public void shouldNotCreateBecauseExists() throws TechnicalException {
        when(newUser.getUsername()).thenReturn(USER_NAME);
        when(userRepository.findByUsername(USER_NAME)).thenReturn(Optional.of(new User()));

        userService.create(newUser, false);

        verify(userRepository, never()).create(any());
    }

    @Test(expected = TechnicalManagementException.class)
    public void shouldNotCreateBecauseTechnicalException() throws TechnicalException {
        when(newUser.getUsername()).thenReturn(USER_NAME);
        when(userRepository.findByUsername(USER_NAME)).thenReturn(Optional.empty());
        when(userRepository.create(any(User.class))).thenThrow(TechnicalException.class);

        userService.create(newUser, false);

        verify(userRepository, never()).create(any());
    }

    @Test(expected = UserNotFoundException.class)
    public void shouldNotConnectBecauseNotExists() throws TechnicalException {
        when(userRepository.findByUsername(USER_NAME)).thenReturn(Optional.empty());

        userService.connect(USER_NAME);

        verify(userRepository, never()).create(any());
    }

    @Test
    public void shouldCreateDefaultApplication() throws TechnicalException {
        userService.setDefaultApplicationForFirstConnection(true);
        when(user.getLastConnectionAt()).thenReturn(null);
        when(userRepository.findByUsername(USER_NAME)).thenReturn(Optional.of(user));

        userService.connect(USER_NAME);

        verify(applicationService, times(1)).create(any(), eq(USER_NAME));
    }

    @Test
    public void shouldNotCreateDefaultApplicationBecauseDisabled() throws TechnicalException {
        userService.setDefaultApplicationForFirstConnection(false);
        when(user.getLastConnectionAt()).thenReturn(null);
        when(userRepository.findByUsername(USER_NAME)).thenReturn(Optional.of(user));

        userService.connect(USER_NAME);

        verify(applicationService, never()).create(any(), eq(USER_NAME));
    }

    @Test
    public void shouldNotCreateDefaultApplicationBecauseAlreadyConnected() throws TechnicalException {
        when(user.getLastConnectionAt()).thenReturn(new Date());
        when(userRepository.findByUsername(USER_NAME)).thenReturn(Optional.of(user));

        userService.connect(USER_NAME);

        verify(applicationService, never()).create(any(), eq(USER_NAME));
    }
}
