package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

import java.util.List;

@Transactional
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    @Modifying
    @Query(value = "ALTER TABLE `pages` AUTO_INCREMENT = 0", nativeQuery = true)
    void resetId();

    @Query(value = "SELECT * FROM `pages` WHERE `path` = :path AND `site_id` = :siteId", nativeQuery = true)
    PageEntity findByPathAndSiteId(String path, int siteId);

    @Query(value = "SELECT * FROM `pages` WHERE `site_id` = :siteId", nativeQuery = true)
    List<PageEntity> findAllPagesBySiteId(int siteId);

    @Query(value = "SELECT COUNT(*) FROM `pages` WHERE `site_id` = :siteId", nativeQuery = true)
    int findCountPagesBySiteId(int siteId);

    @Modifying
    @Query(value = "DELETE FROM `pages` WHERE `site_id` = :siteId", nativeQuery = true)
    void deletePagesBySiteId(int siteId);


}
