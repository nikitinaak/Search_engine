package searchengine.webHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.JsoupSetting;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.RecursiveTask;

public class RecursiveParser extends RecursiveTask<Set<String>> {
    private static Logger logger = LogManager.getLogger(RecursiveParser.class);
    private static Marker FIND_LINK_MARKER = MarkerManager.getMarker("FIND_LINK");

    private static volatile boolean stopped;

    private final Link currentLink;
    private volatile boolean firstStart;
    private JsoupSetting jsoupSettings;

    private static ConcurrentSkipListSet<Link> visitedLink = new ConcurrentSkipListSet<>();
    private static ConcurrentSkipListSet<String> linksSet = new ConcurrentSkipListSet<>();

    public RecursiveParser(Link currentLink, boolean firstStart, JsoupSetting jsoupSettings) {
        this.currentLink = currentLink;
        this.firstStart = firstStart;
        this.jsoupSettings = jsoupSettings;
        visitedLink.add(currentLink);
        linksSet.add(currentLink.getLink());
        parse();
    }

    private void parse() {
        if (!firstStart) {
            visitedLink = new ConcurrentSkipListSet<>();
            linksSet = new ConcurrentSkipListSet<>();
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
                if (isWorkableLink(newLink)) {
                    currentLink.addChild(newLink);
                    linksSet.add(newLink.getLink());
                    logger.info(FIND_LINK_MARKER, newLink.getLink());
                }
                if (stopped) {
                    return;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public static void setStopped(boolean stopped) {
        RecursiveParser.stopped = stopped;
    }

    private boolean isWorkableLink(Link link) {
        return  !visitedLink.contains(link) && !linksSet.contains(link.getLink())
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
    protected Set<String> compute() {
        Set<String> links = new HashSet<>();
        links.add(currentLink.getLink());
        ArrayList<RecursiveParser> tasks = new ArrayList<>();
        for (Link child : currentLink.getChildren()) {
            RecursiveParser task = new RecursiveParser(child, firstStart, jsoupSettings);
            if (stopped) {
                break;
            }
            task.fork();
            tasks.add(task);
        }
        for (RecursiveParser task : tasks) {
            if (stopped) {
                tasks.clear();
                break;
            }
            links.addAll(task.join());
        }
        return links;
    }
}
