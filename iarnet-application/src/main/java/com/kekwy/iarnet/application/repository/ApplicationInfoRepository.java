package com.kekwy.iarnet.application.repository;

import com.kekwy.iarnet.application.model.ApplicationInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationInfoRepository extends JpaRepository<ApplicationInfoEntity, String> {

    /** 按状态统计数量 */
    long countByStatus(String status);

    /** 按创建时间倒序查询 */
    List<ApplicationInfoEntity> findAllByOrderByCreatedAtDesc();

}
