package neo4j.util;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.helpers.collection.Pair;

import java.util.ArrayList;
import java.util.List;

import static org.neo4j.graphdb.Direction.BOTH;
import static org.neo4j.graphdb.Direction.INCOMING;
import static org.neo4j.graphdb.Direction.OUTGOING;

/**
 * Created by andrewbowman on 1/12/18.
 */
public abstract class RelTypeAndDirection {

    public static final char BACKTICK = '`';

    public static List<Pair<RelationshipType, Direction>> parse(String pathFilter) {
        List<Pair<RelationshipType, Direction>> relsAndDirs = new ArrayList<>();
        if (pathFilter == null) {
            relsAndDirs.add(Pair.of(null, BOTH));
        } else {
            String[] defs = pathFilter.split("\\|");
            for (String def : defs) {
                relsAndDirs.add(Pair.of(relationshipTypeFor(def), directionFor(def)));
            }
        }
        return relsAndDirs;
    }

    private static Direction directionFor(String type) {
        if (type.contains("<")) return INCOMING;
        if (type.contains(">")) return OUTGOING;
        return BOTH;
    }

    private static RelationshipType relationshipTypeFor(String name) {
        if (name.indexOf(BACKTICK) > -1) name = name.substring(name.indexOf(BACKTICK)+1,name.lastIndexOf(BACKTICK));
        else {
            name = name.replaceAll("[<>:]", "");
        }
        return name.trim().isEmpty() ? null : RelationshipType.withName(name);
    }
}