package neo4j.path.util;

import neo4j.result.NodeResult;
import neo4j.result.PathResult;
import neo4j.util.Util;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;
import org.neo4j.helpers.collection.Pair;
import org.neo4j.kernel.api.exceptions.EntityNotFoundException;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.neo4j.graphdb.traversal.Evaluation.*;


public class DenseNodeFinder {
    public static final Uniqueness UNIQUENESS = Uniqueness.RELATIONSHIP_PATH;
    @Context
    public GraphDatabaseAPI db;

    @Context
    public Log log;

    @Procedure("expandTo.denseNodes.paths")
    @Description("expandTo.denseNodes.paths(startNode <id>|Node|list, {minLevel, maxLevel, relationshipFilter, labelFilter, uniqueness:'RELATIONSHIP_PATH', bfs:true, filterStartNode:false, optional:false, density:1000, denseRels}) yield path expand paths from start node to dense nodes (with denseRels of the given density or higher) following the given relationships from min to max-level adhering to the label filters")
    public Stream<PathResult> denseNodesPaths(@Name("start") Object start, @Name("config") Map<String,Object> config) throws Exception {
        return expandConfigPrivate(start, config).map( PathResult::new );
    }

    @Procedure("expandTo.denseNodes.nodes")
    @Description("expandTo.denseNodes.nodes(startNode <id>|Node|list, {maxLevel, relationshipFilter, labelFilter, bfs:true, filterStartNode:false, optional:false, density:1000, denseRels}) yield node expand to dense nodes (with denseRels of the given density or higher) reachable from start node following relationships to max-level adhering to the label filters")
    public Stream<NodeResult> denseNodes(@Name("start") Object start, @Name("config") Map<String,Object> config) throws Exception {
        Map<String, Object> configMap = new HashMap<>(config);
        configMap.remove("minLevel");
        configMap.put("uniqueness", "NODE_GLOBAL");

        return expandConfigPrivate(start, configMap).map( path -> path == null ? new NodeResult(null) : new NodeResult(path.endNode()) );
    }

    @Procedure("expandTo.denseNodes.singlePath")
    @Description("expandTo.denseNodes.singlePath(startNode <id>|Node|list, {maxLevel, relationshipFilter, labelFilter, bfs:true, filterStartNode:false, optional:false, density:1000, denseRels}) yield path expand a single path to each dense nodes (with denseRels of the given density or higher) from start node following relationships to max-level adhering to the label filters")
    public Stream<PathResult> denseNodesSinglePath(@Name("start") Object start, @Name("config") Map<String,Object> config) throws Exception {
        Map<String, Object> configMap = new HashMap<>(config);
        configMap.remove("minLevel");
        configMap.put("uniqueness", "NODE_GLOBAL");

        return expandConfigPrivate(start, configMap).map( PathResult::new );
    }

    private Uniqueness getUniqueness(String uniqueness) {
        for (Uniqueness u : Uniqueness.values()) {
            if (u.name().equalsIgnoreCase(uniqueness)) return u;
        }
        return UNIQUENESS;
    }

    /*
    , @Name("relationshipFilter") String pathFilter
    , @Name("labelFilter") String labelFilter
    , @Name("minLevel") long minLevel
    , @Name("maxLevel") long maxLevel ) throws Exception {
     */
    @SuppressWarnings("unchecked")
    private List<Node> startToNodes(Object start) throws Exception {
        if (start == null) return Collections.emptyList();
        if (start instanceof Node) {
            return Collections.singletonList((Node) start);
        }
        if (start instanceof Number) {
            return Collections.singletonList(db.getNodeById(((Number) start).longValue()));
        }
        if (start instanceof List) {
            List list = (List) start;
            if (list.isEmpty()) return Collections.emptyList();

            Object first = list.get(0);
            if (first instanceof Node) return (List<Node>)list;
            if (first instanceof Number) {
                List<Node> nodes = new ArrayList<>();
                for (Number n : ((List<Number>)list)) nodes.add(db.getNodeById(n.longValue()));
                return nodes;
            }
        }
        throw new Exception("Unsupported data type for start parameter a Node or an Identifier (long) of a Node must be given!");
    }

    private Stream<Path> expandConfigPrivate(@Name("start") Object start, @Name("config") Map<String,Object> config) throws Exception {
        List<Node> nodes = startToNodes(start);

        String uniqueness = (String) config.getOrDefault("uniqueness", UNIQUENESS.name());
        String relationshipFilter = (String) config.getOrDefault("relationshipFilter", null);
        String labelFilter = (String) config.getOrDefault("labelFilter", null);
        long minLevel = Util.toLong(config.getOrDefault("minLevel", "-1"));
        long maxLevel = Util.toLong(config.getOrDefault("maxLevel", "-1"));
        boolean bfs = Util.toBoolean(config.getOrDefault("bfs",true));
        boolean filterStartNode = Util.toBoolean(config.getOrDefault("filterStartNode", false));
        long limit = Util.toLong(config.getOrDefault("limit", "-1"));
        boolean optional = Util.toBoolean(config.getOrDefault("optional", false));
        long degree = Util.toLong(config.getOrDefault("degree", "1000"));
        long continueBelow = Util.toLong(config.getOrDefault("continueBelow", 0));
        String denseRelPattern = (String) config.getOrDefault("denseRels", "");

        Stream<Path> results = explorePathPrivate(nodes, relationshipFilter, labelFilter, minLevel, maxLevel, bfs, getUniqueness(uniqueness), filterStartNode, limit, denseRelPattern, degree, continueBelow);

        if (optional) {
            return optionalStream(results);
        } else {
            return results;
        }
    }

    private Stream<Path> explorePathPrivate(Iterable<Node> startNodes
            , String pathFilter
            , String labelFilter
            , long minLevel
            , long maxLevel, boolean bfs, Uniqueness uniqueness, boolean filterStartNode, long limit, String denseRelPattern, long degree, long continueBelow) {
        // LabelFilter
        // -|Label|:Label|:Label excluded label list
        // +:Label or :Label include labels

        Traverser traverser = traverse(db.traversalDescription(), startNodes, pathFilter, labelFilter, minLevel, maxLevel, uniqueness,bfs,filterStartNode,limit,denseRelPattern, degree, continueBelow);
        return traverser.stream();
    }

    /**
     * If the stream is empty, returns a stream of a single null value, otherwise returns the equivalent of the input stream
     * @param stream the input stream
     * @return a stream of a single null value if the input stream is empty, otherwise returns the equivalent of the input stream
     */
    private Stream<Path> optionalStream(Stream<Path> stream) {
        Stream<Path> optionalStream;
        Iterator<Path> itr = stream.iterator();
        if (itr.hasNext()) {
            optionalStream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(itr, 0), false);
        } else {
            List<Path> listOfNull = new ArrayList<>();
            listOfNull.add(null);
            optionalStream = listOfNull.stream();
        }

        return optionalStream;
    }

    public static Traverser traverse(TraversalDescription traversalDescription, Iterable<Node> startNodes, String pathFilter, String labelFilter, long minLevel, long maxLevel, Uniqueness uniqueness, boolean bfs, boolean filterStartNode, long limit, String denseRelPattern, long degree, long continueBelow) {
        TraversalDescription td = traversalDescription;
        // based on the pathFilter definition now the possible relationships and directions must be shown

        td = bfs ? td.breadthFirst() : td.depthFirst();

        Iterable<Pair<RelationshipType, Direction>> relDirIterable = neo4j.util.RelTypeAndDirection.parse(pathFilter);

        for (Pair<RelationshipType, Direction> pair: relDirIterable) {
            if (pair.first() == null) {
                td = td.expand(PathExpanderBuilder.allTypes(pair.other()).build());
            } else {
                td = td.relationships(pair.first(), pair.other());
            }
        }

        if (minLevel != -1) td = td.evaluator(Evaluators.fromDepth((int) minLevel));
        if (maxLevel != -1) td = td.evaluator(Evaluators.toDepth((int) maxLevel));

        if (labelFilter != null && !labelFilter.trim().isEmpty()) {
            td = td.evaluator(new LabelEvaluator(labelFilter, filterStartNode, limit, (int) minLevel));
        }

        td = td.evaluator(new DenseNodeEvaluator(denseRelPattern, degree, continueBelow, filterStartNode));

        td = td.uniqueness(uniqueness); // this is how Cypher works !! Uniqueness.RELATIONSHIP_PATH
        // uniqueness should be set as last on the TraversalDescription
        return td.traverse(startNodes);
    }

    public static class DenseNodeEvaluator implements Evaluator {
        private String denseRelPattern;
        private long degree;
        private long continueBelow;
        private boolean filterStartNode;


        public DenseNodeEvaluator(String denseRelPattern, long degree, long continueBelow, boolean filterStartNode) {
            this.denseRelPattern = denseRelPattern;
            this.degree = degree;
            this.continueBelow = continueBelow;
            this.filterStartNode = filterStartNode;
        }


        @Override
        public Evaluation evaluate(Path path) {
            Node check = path.endNode();
            long degree = 0;

            if (path.length() == 0 && !filterStartNode) {
                return EXCLUDE_AND_CONTINUE;
            }

            try {
                degree = Util.degree(check, denseRelPattern);
            } catch (EntityNotFoundException e) {
                e.printStackTrace();
                return EXCLUDE_AND_PRUNE;
            }

            if (degree >= this.degree) {
                if (degree >= continueBelow) {
                    return INCLUDE_AND_PRUNE;
                } else {
                    return INCLUDE_AND_CONTINUE;
                }

            } else {
                return EXCLUDE_AND_CONTINUE;
            }
        }
    }

    public static class LabelEvaluator implements Evaluator {
        private Set<String> whitelistLabels;
        private Set<String> blacklistLabels;
        private boolean filterStartNode;
        private long limit = -1;
        private long minLevel = -1;
        private long resultCount = 0;

        public LabelEvaluator(String labelFilter, boolean filterStartNode, long limit, int minLevel) {
            this.filterStartNode = filterStartNode;
            this.limit = limit;
            this.minLevel = minLevel;
            Map<Character, Set<String>> labelMap = new HashMap<>(4);

            if (labelFilter !=  null && !labelFilter.isEmpty()) {

                // parse the filter
                // split on |
                String[] defs = labelFilter.split("\\|");
                Set<String> labels = null;

                for (String def : defs) {
                    char operator = def.charAt(0);
                    switch (operator) {
                        case '+':
                        case '-':
                            labels = labelMap.computeIfAbsent(operator, character -> new HashSet<>());
                            def = def.substring(1);
                    }

                    if (def.startsWith(":")) {
                        def = def.substring(1);
                    }

                    if (!def.isEmpty()) {
                        labels.add(def);
                    }
                }
            }

            whitelistLabels = labelMap.computeIfAbsent('+', character -> Collections.emptySet());
            blacklistLabels = labelMap.computeIfAbsent('-', character -> Collections.emptySet());
        }

        @Override
        public Evaluation evaluate(Path path) {
            int depth = path.length();
            Node check = path.endNode();

            // if start node shouldn't be filtered, exclude/include based on if using termination/endnode filter or not
            // minLevel evaluator will separately enforce exclusion if we're below minLevel
            if (depth == 0 && !filterStartNode) {
                return EXCLUDE_AND_CONTINUE;
            }

            // below minLevel always exclude; continue if blacklist and whitelist allow it
            if (depth < minLevel) {
                return labelExists(check, blacklistLabels) || !whitelistAllowed(check) ? EXCLUDE_AND_PRUNE : EXCLUDE_AND_CONTINUE;
            }

            // cut off expansion when we reach the limit
            if (limit != -1 && resultCount >= limit) {
                return INCLUDE_AND_PRUNE;
            }

            Evaluation result = labelExists(check, blacklistLabels) ? EXCLUDE_AND_PRUNE :
                    whitelistAllowed(check) ? EXCLUDE_AND_CONTINUE : EXCLUDE_AND_PRUNE;

            return result;
        }

        private boolean labelExists(Node node, Set<String> labels) {
            if (labels.isEmpty()) {
                return false;
            }

            for ( Label lab : node.getLabels() ) {
                if (labels.contains(lab.name())) {
                    return true;
                }
            }
            return false;
        }

        private boolean whitelistAllowed(Node node) {
            return whitelistLabels.isEmpty() || labelExists(node, whitelistLabels);
        }
    }
}