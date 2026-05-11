package com.opsflow.org_service.infrastructure.adapters;

import com.opsflow.org_service.domain.models.LocationDomain;
import com.opsflow.org_service.domain.ports.out.LocationRepositoryPort;
import com.opsflow.org_service.infrastructure.entities.Location;
import com.opsflow.org_service.infrastructure.entities.Organization;
import com.opsflow.org_service.infrastructure.mappers.LocationMapper;
import com.opsflow.org_service.infrastructure.repositories.LocationRepository;
import com.opsflow.org_service.infrastructure.repositories.OrganizationRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LocationPersistenceAdapter implements LocationRepositoryPort {

    private final LocationRepository locationRepository;
    private final OrganizationRepository organizationRepository;
    private final LocationMapper locationMapper;

    public LocationPersistenceAdapter(LocationRepository locationRepository,
                                      OrganizationRepository organizationRepository,
                                      LocationMapper locationMapper) {
        this.locationRepository = locationRepository;
        this.organizationRepository = organizationRepository;
        this.locationMapper = locationMapper;
    }

    @Override
    public LocationDomain save(LocationDomain domain) {
        Location entity = locationMapper.toEntity(domain);

        Organization organization = organizationRepository.findById(domain.getOrganizationId())
                .orElseThrow(() -> new RuntimeException("Organización no encontrada con ID: " + domain.getOrganizationId()));

        entity.setOrganization(organization);
        Location savedEntity = locationRepository.save(entity);
        return locationMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<LocationDomain> findById(Long id) {
        return locationRepository.findById(id).map(locationMapper::toDomain);
    }

    @Override
    public List<LocationDomain> findAll() {
        return locationRepository.findAll().stream()
                .map(locationMapper::toDomain)
                .toList();
    }

    @Override
    public List<LocationDomain> findByOrganizationId(Long organizationId) {
        return locationRepository.findByOrganizationId(organizationId).stream()
                .map(locationMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        locationRepository.deleteById(id);
    }
}
