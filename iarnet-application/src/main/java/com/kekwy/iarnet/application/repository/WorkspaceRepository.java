package com.kekwy.iarnet.application.repository;

import com.kekwy.iarnet.application.model.WorkspaceEntity;
import com.kekwy.iarnet.model.ID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, String> {

    Optional<WorkspaceEntity> findByApplicationID(String applicationID);

}
