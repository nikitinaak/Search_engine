package searchengine.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

@Transactional
public interface PageRepository extends PagingAndSortingRepository<PageEntity, Integer> {

    @Modifying
    @Query(value = "ALTER TABLE `pages` AUTO_INCREMENT = 0", nativeQuery = true)
    void resetId();

    @Query(value = "SELECT * FROM `pages` WHERE `path` = :path AND `site_id` = :siteId", nativeQuery = true)
    Optional<PageEntity> findByPathAndSiteId(String path, int siteId);

    int countAllPageEntityBySite(SiteEntity site);

    List<PageEntity> findFirst1000PageEntityBySite(SiteEntity site);

    @Query(value = "SELECT * FROM `pages` WHERE `id` IN :ids", nativeQuery = true)
    List<PageEntity> findAllById(@Param("ids") List<Integer> ids, Pageable pageable);
}
