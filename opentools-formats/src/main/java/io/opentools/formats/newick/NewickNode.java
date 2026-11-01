package io.opentools.formats.newick;

import java.util.ArrayList;
import java.util.List;

public final class NewickNode {
    private final String name;
    private final Double length;
    private final List<NewickNode> children;

    public NewickNode(String name, Double length, List<NewickNode> children) {
        this.name = name == null ? "" : name;
        this.length = length;
        this.children = children == null ? new ArrayList<>() : new ArrayList<>(children);
    }

    public String name() { return name; }
    public Double length() { return length; }
    public List<NewickNode> children() { return List.copyOf(children); }
    public boolean leaf() { return children.isEmpty(); }

    public void addChild(NewickNode child) {
        children.add(child);
    }

    public void removeChildren(java.util.Collection<NewickNode> values) {
        children.removeAll(values);
    }
}
