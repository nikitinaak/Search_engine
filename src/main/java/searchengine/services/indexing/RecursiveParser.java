package searchengine.services.indexing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.JsoupSetting;
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

    private static volatile boolean stopped;

    private final Link currentLink;
    private volatile boolean firstStart;

    private SiteEntity siteEntity;
    private PageRepository pageRepository;
    private JsoupSetting jsoupSettings;

    private static ConcurrentSkipListSet<Link> visitedLink = new ConcurrentSkipListSet<>();

    public RecursiveParser(Link currentLink, boolean firstStart, SiteEntity siteEntity,
                           PageRepository pageRepository, JsoupSetting jsoupSettings) {
        this.currentLink = currentLink;
        this.firstStart = firstStart;
        this.siteEntity = siteEntity;
        this.pageRepository = pageRepository;
        this.jsoupSettings = jsoupSettings;
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
                if (isWorkableLink(newLink) && notExistInDB(connector)) {
                    PageEntity pageEntity = new PageEntity(siteEntity, connector.getPathByUrl(),
                            connector.getStatusConnectionCode(), connector.getContent());
                    if (isStatusCodeWrong(pageEntity.getCode())) {
                        continue;
                    }
                    pageRepository.save(pageEntity);
                    currentLink.addChild(newLink);
                    logger.info(FIND_LINK_MARKER, newLink.getLink());
                }
                if (stopped) {
                    return;
                }
            }
        } catch (Exception e) {
            logger.error(e.getStackTrace());
            logger.error(e.getMessage());
        }
    }

    public static void setStopped(boolean stopped) {
        RecursiveParser.stopped = stopped;
    }

    private boolean isStatusCodeWrong(int code) {
        return String.valueOf(code).startsWith("4") || String.valueOf(code).startsWith("5");
    }

    private boolean notExistInDB(JsoupConnector connector) throws MalformedURLException {
        return pageRepository.findByPathAndSiteId(connector.getPathByUrl(),
                siteEntity.getSiteId()) == null;
    }

    private boolean isWorkableLink(Link link) {
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
            RecursiveParser task = new RecursiveParser(child, firstStart, siteEntity,
                    pageRepository, jsoupSettings);
            if (stopped) {
                return;
            }
            tasks.add(task);
        }
        for (RecursiveParser task : tasks) {
            if (stopped) {
                tasks.clear();
                return;
            }
            task.fork();
        }
        for (RecursiveParser task : tasks) {
            if (stopped) {
                tasks.clear();
                return;
            }
            task.join();
        }
    }
}
