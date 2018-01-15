package neo4j.result;

import org.neo4j.graphdb.Path;

/**
 * Created by andrewbowman on 1/12/18.
 */
public class PathResult {
    public Path path;

    public PathResult(Path path) {
        this.path = path;
    }
}
