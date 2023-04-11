package searchengine.dto.indexing;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class Link implements Comparable{
    private String link;
    private Set<Link> children;

    public Link(String link) {
        this.link = link;
        children = new HashSet<>();
    }

    public void addChild(Link child) {
        children.add(child);
    }

    @Override
    public int compareTo(Object o) {
        Link link = (Link) o;
        return this.link.compareTo(link.getLink());
    }

    public String getHost() {
        String[] array = link.split(":?/+");
        return array[0] + "://" + array[1];
    }
}
