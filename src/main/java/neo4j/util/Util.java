package neo4j.util;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.helpers.collection.Pair;
import org.neo4j.kernel.api.exceptions.EntityNotFoundException;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;

import static neo4j.util.RelTypeAndDirection.parse;

/**
 * Created by andrewbowman on 1/8/18.
 */
public class Util {
    public static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number)value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean toBoolean(Object value) {
        if ((value == null || value instanceof Number && (((Number) value).longValue()) == 0L || value instanceof String && (value.equals("") || ((String) value).equalsIgnoreCase("false") || ((String) value).equalsIgnoreCase("no")|| ((String) value).equalsIgnoreCase("0"))|| value instanceof Boolean && value.equals(false))) {
            return false;
        }
        return true;
    }

    @Description("apoc.node.degree(node, rel-direction-pattern) - returns total degrees of the given relationships in the pattern, can use '>' or '<' for all outgoing or incoming relationships")
    public static long degree(@Name("node") Node node, @Name(value = "types",defaultValue = "") String types) throws EntityNotFoundException {
        if (types==null || types.isEmpty()) return node.getDegree();
        long degree = 0;
        for (Pair<RelationshipType, Direction> pair : parse(types)) {
            degree += getDegreeSafe(node, pair.first(), pair.other());
        }
        return degree;
    }

    // works in cases when relType is null
    private static int getDegreeSafe(Node node, RelationshipType relType, Direction direction) {
        if (relType == null) {
            return node.getDegree(direction);
        }

        return node.getDegree(relType, direction);
    }
}
