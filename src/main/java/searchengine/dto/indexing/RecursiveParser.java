package searchengine.dto.indexing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.Jsoup;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.RecursiveAction;

public class RecursiveParser extends RecursiveAction {
    private static Logger logger = LogManager.getLogger(RecursiveParser.class);
    private static Marker FIND_LINK_MARKER = MarkerManager.getMarker("FIND_LINK");

    private final Link currentLink;
    private final Jsoup jsoupSettings;
    private final SiteEntity siteEntity;
    private final PageRepository pageRepository;
    private volatile boolean firstStart;
    private volatile boolean stopped;

    private static ConcurrentSkipListSet<Link> visitedLink = new ConcurrentSkipListSet<>();

    public RecursiveParser(Link currentLink, boolean firstStart, boolean stopped,
                           Jsoup jsoupSettings, SiteEntity siteEntity, PageRepository pageRepository) {
        this.currentLink = currentLink;
        this.jsoupSettings = jsoupSettings;
        this.siteEntity = siteEntity;
        this.pageRepository = pageRepository;
        this.firstStart = firstStart;
        this.stopped = stopped;
        visitedLink.add(currentLink);
        parse();
    }

    private void parse() {
        if (!firstStart) {
            visitedLink = new ConcurrentSkipListSet<>();
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
        }
        try {
            Document doc = new JsoupConnector(currentLink.getLink(), jsoupSettings).getConnection();
            Elements elements = doc.select("a");
            for (Element element : elements) {
                Link newLink = new Link(element.attr("href"));
                if (newLink.getLink().startsWith("/") && !newLink.getLink().equals("/")) {
                    newLink.setLink(currentLink.getHost() + newLink.getLink());
                }
                JsoupConnector connector = new JsoupConnector(newLink.getLink(), jsoupSettings);
                if (workableLink(newLink) && notExistInDB(connector)) {
                    pageRepository.save(new PageEntity(siteEntity, connector.getPathByUrl(),
                            connector.getStatusConnectionCode(), connector.getContent()));

                    currentLink.addChild(newLink);
                    logger.info(FIND_LINK_MARKER, newLink.getLink());
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private boolean notExistInDB(JsoupConnector connector) throws MalformedURLException {
        return pageRepository.findByPathAndSiteId(connector.getPathByUrl(),
                siteEntity.getSiteId()) == null;
    }

    private boolean workableLink(Link link) {
        return  !visitedLink.contains(link)
                && !link.getLink().equals(currentLink.getLink())
                && link.getLink().startsWith(currentLink.getHost())
                && !link.getLink().endsWith("jpg") && !link.getLink().endsWith("png")
                && !link.getLink().endsWith("gif") && !link.getLink().endsWith("bmp")
                && !link.getLink().endsWith("pdf") && !link.getLink().endsWith("xml")
                && !link.getLink().endsWith("mp4") && !link.getLink().endsWith("jpeg")
                && !link.getLink().contains("?") && !link.getLink().isEmpty()
                && !link.getLink().contains("#");
    }

    @Override
    protected void compute() {
        ArrayList<RecursiveParser> tasks = new ArrayList<>();
        for (Link child : currentLink.getChildren()) {
            RecursiveParser task = new RecursiveParser(child, firstStart, stopped,
                    jsoupSettings, siteEntity, pageRepository);
            if (task.stopped) {
                visitedLink = new ConcurrentSkipListSet<>();
                return;
            }
            tasks.add(task);
        }
        for (RecursiveParser task : tasks) {
            if (task.stopped) {
                tasks.clear();
                visitedLink = new ConcurrentSkipListSet<>();
                return;
            }
            task.fork();
        }
        for (RecursiveParser task : tasks) {
            if (task.stopped) {
                tasks.clear();
                visitedLink = new ConcurrentSkipListSet<>();
                return;
            }
            task.join();
        }
    }
}
