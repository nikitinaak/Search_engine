package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

@Transactional
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    @Modifying
    @Query(value = "ALTER TABLE `sites` AUTO_INCREMENT = 0", nativeQuery = true)
    void resetId();

    @Query(value = "SELECT * FROM `sites` WHERE `url` = :url", nativeQuery = true)
    SiteEntity findSiteEntityByUrl(String url);
}
