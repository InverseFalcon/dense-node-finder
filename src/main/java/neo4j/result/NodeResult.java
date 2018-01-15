package neo4j.result;

import org.neo4j.graphdb.Node;

/**
 * Created by andrewbowman on 1/12/18.
 */
public class NodeResult {
    public final Node node;

    public NodeResult(Node node) {
        this.node = node;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && getClass() == o.getClass() && node.equals(((neo4j.result.NodeResult) o).node);
    }

    @Override
    public int hashCode() {
        return node.hashCode();
    }
}
