package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;

@Transactional
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    @Modifying
    @Query(value = "ALTER TABLE `lemmas` AUTO_INCREMENT = 0", nativeQuery = true)
    void resetId();

    @Query(value = "SELECT * FROM `lemmas` WHERE `lemma` = :lemma AND `site_id` = :siteId", nativeQuery = true)
    LemmaEntity findByLemmaAndSiteId(String lemma, int siteId);

    @Query(value = "SELECT COUNT(*) FROM `lemmas` WHERE `site_id` = :siteId", nativeQuery = true)
    int findCountLemmasBySiteId(int siteId);

    @Modifying
    @Query(value = "DELETE FROM `lemmas` WHERE `site_id` = :siteId", nativeQuery = true)
    void deleteBySiteId(int siteId);
}
