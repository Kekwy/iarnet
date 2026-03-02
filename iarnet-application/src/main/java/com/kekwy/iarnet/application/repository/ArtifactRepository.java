package com.kekwy.iarnet.application.repository;

import com.kekwy.iarnet.application.model.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactRepository extends JpaRepository<ArtifactEntity, String> {
}
