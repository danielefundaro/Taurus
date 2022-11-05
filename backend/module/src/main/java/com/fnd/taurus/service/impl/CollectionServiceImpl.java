package com.fnd.taurus.service.impl;

import com.fnd.taurus.dto.CollectionDTO;
import com.fnd.taurus.entity.Collection;
import com.fnd.taurus.repository.AuditRepository;
import com.fnd.taurus.repository.CollectionRepository;
import com.fnd.taurus.service.CollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CollectionServiceImpl implements CollectionService {
    private final CollectionRepository collectionRepository;
    private final AuditRepository auditRepository;

    @Autowired
    public CollectionServiceImpl(CollectionRepository collectionRepository, AuditRepository auditRepository) {
        this.collectionRepository = collectionRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public AuditRepository getAuditRepository() {
        return auditRepository;
    }

    @Override
    public CollectionRepository getRepository() {
        return collectionRepository;
    }

    @Override
    public Class<Collection> getEntity() {
        return Collection.class;
    }

    @Override
    public Class<CollectionDTO> getDTO() {
        return CollectionDTO.class;
    }
}
