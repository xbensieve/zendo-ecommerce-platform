package com.zendo.vendor.infrastructure.persistence;

import com.zendo.vendor.domain.Vendor;
import com.zendo.vendor.domain.VendorId;
import com.zendo.vendor.domain.VendorName;
import com.zendo.vendor.domain.VendorStatus;
import com.zendo.vendor.domain.VendorRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class VendorRepositoryImpl implements VendorRepository {

    private final SpringDataVendorRepository jpaRepository;

    public VendorRepositoryImpl(SpringDataVendorRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Vendor vendor) {
        VendorEntity entity = new VendorEntity(
            vendor.getId().value(),
            vendor.getName().value(),
            vendor.getStatus().name(),
            vendor.getOwnerUserId()
        );
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Vendor> findById(VendorId id) {
        return jpaRepository.findById(id.value())
            .map(entity -> Vendor.reconstitute(
                VendorId.fromString(entity.getId().toString()),
                new VendorName(entity.getName()),
                VendorStatus.valueOf(entity.getStatus()),
                entity.getOwnerUserId()
            ));
    }
}
