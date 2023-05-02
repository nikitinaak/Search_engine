package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;

import java.util.List;

@Transactional
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    @Modifying
    @Query(value = "ALTER TABLE `indexest` AUTO_INCREMENT = 0", nativeQuery = true)
    void resetId();

    @Modifying
    @Query(value = "DELETE FROM `indexest` WHERE `page_id` = :pageId", nativeQuery = true)
    void deleteByPage(int pageId);

    @Query(value = "SELECT * FROM `indexest` WHERE `page_id` = :pageId", nativeQuery = true)
    List<IndexEntity> findAllIndexEntityByPageId(int pageId);
}
