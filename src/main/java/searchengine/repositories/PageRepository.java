package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

@Repository
@Transactional
public interface PageRepository extends CrudRepository<PageEntity, Integer> {

    @Modifying
    @Query(value = "ALTER TABLE `pages` AUTO_INCREMENT = 0", nativeQuery = true)
    void resetId();

    @Query(value = "SELECT * FROM `pages` WHERE `path` = :path AND `site_id` = :siteId", nativeQuery = true)
    PageEntity findByPathAndSiteId(String path, int siteId);

    @Query(value = "SELECT * FROM `pages` WHERE `id` = :id", nativeQuery = true)
    PageEntity findPageById(int id);

    @Modifying
    @Query(value = "DELETE FROM `pages` WHERE `id` = :id", nativeQuery = true)
    void deletePageById(int id);
}
