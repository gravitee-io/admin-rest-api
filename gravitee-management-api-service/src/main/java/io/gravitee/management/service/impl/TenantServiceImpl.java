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
package io.gravitee.management.service.impl;

import io.gravitee.common.utils.IdGenerator;
import io.gravitee.management.model.NewTenantEntity;
import io.gravitee.management.model.TenantEntity;
import io.gravitee.management.model.UpdateTenantEntity;
import io.gravitee.management.service.TenantService;
import io.gravitee.management.service.exceptions.DuplicateTenantNameException;
import io.gravitee.management.service.exceptions.TechnicalManagementException;
import io.gravitee.management.service.exceptions.TenantNotFoundException;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.TenantRepository;
import io.gravitee.repository.management.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class TenantServiceImpl extends TransactionalService implements TenantService {

    private final Logger LOGGER = LoggerFactory.getLogger(TenantServiceImpl.class);

    @Autowired
    private TenantRepository tenantRepository;

    @Override
    public TenantEntity findById(String tenantId) {
        try {
            LOGGER.debug("Find tenant by ID: {}", tenantId);
            Optional<Tenant> optTenant = tenantRepository.findById(tenantId);

            if (! optTenant.isPresent()) {
                throw new TenantNotFoundException(tenantId);
            }

            return convert(optTenant.get());
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find tenant by ID", ex);
            throw new TechnicalManagementException("An error occurs while trying to find tenant by ID", ex);
        }
    }

    @Override
    public List<TenantEntity> findAll() {
        try {
            LOGGER.debug("Find all tenants");
            return tenantRepository.findAll()
                    .stream()
                    .map(this::convert).collect(Collectors.toList());
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find all tenants", ex);
            throw new TechnicalManagementException("An error occurs while trying to find all tenants", ex);
        }
    }

    @Override
    public List<TenantEntity> create(final List<NewTenantEntity> tenantEntities) {
        // First we prevent the duplicate tenant name
        final List<String> tenantNames = tenantEntities.stream()
                .map(NewTenantEntity::getName)
                .collect(Collectors.toList());

        final Optional<TenantEntity> optionalTenant = findAll().stream()
                .filter(tenant -> tenantNames.contains(tenant.getName()))
                .findAny();

        if (optionalTenant.isPresent()) {
            throw new DuplicateTenantNameException(optionalTenant.get().getName());
        }

        final List<TenantEntity> savedTenants = new ArrayList<>(tenantEntities.size());
        tenantEntities.forEach(tenantEntity -> {
            try {
                savedTenants.add(convert(tenantRepository.create(convert(tenantEntity))));
            } catch (TechnicalException ex) {
                LOGGER.error("An error occurs while trying to create tenant {}", tenantEntity.getName(), ex);
                throw new TechnicalManagementException("An error occurs while trying to create tenant " + tenantEntity.getName(), ex);
            }
        });
        return savedTenants;
    }

    @Override
    public List<TenantEntity> update(final List<UpdateTenantEntity> tenantEntities) {
        final List<TenantEntity> savedTenants = new ArrayList<>(tenantEntities.size());
        tenantEntities.forEach(tenantEntity -> {
            try {
                savedTenants.add(convert(tenantRepository.update(convert(tenantEntity))));
            } catch (TechnicalException ex) {
                LOGGER.error("An error occurs while trying to update tenant {}", tenantEntity.getName(), ex);
                throw new TechnicalManagementException("An error occurs while trying to update tenant " + tenantEntity.getName(), ex);
            }
        });
        return savedTenants;
    }

    @Override
    public void delete(final String tenantId) {
        try {
            tenantRepository.delete(tenantId);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to delete tenant {}", tenantId, ex);
            throw new TechnicalManagementException("An error occurs while trying to delete tenant " + tenantId, ex);
        }
    }

    private Tenant convert(final NewTenantEntity tenantEntity) {
        final Tenant tenant = new Tenant();
        tenant.setId(IdGenerator.generate(tenantEntity.getName()));
        tenant.setName(tenantEntity.getName());
        tenant.setDescription(tenantEntity.getDescription());
        return tenant;
    }

    private Tenant convert(final UpdateTenantEntity tenantEntity) {
        final Tenant tenant = new Tenant();
        tenant.setId(tenantEntity.getId());
        tenant.setName(tenantEntity.getName());
        tenant.setDescription(tenantEntity.getDescription());
        return tenant;
    }

    private TenantEntity convert(final Tenant tenant) {
        final TenantEntity tenantEntity = new TenantEntity();
        tenantEntity.setId(tenant.getId());
        tenantEntity.setName(tenant.getName());
        tenantEntity.setDescription(tenant.getDescription());
        return tenantEntity;
    }
}
