package neo4j.path.util;

import static org.hamcrest.CoreMatchers.equalTo;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.v1.*;
import org.neo4j.harness.junit.Neo4jRule;

import java.util.List;

import static org.junit.Assert.assertThat;

/**
 * Created by andrewbowman on 1/8/18.
 */
public class DenseNodeFinderTest {
    @Rule
    public Neo4jRule neo4j = new Neo4jRule()
            .withProcedure( DenseNodeFinder.class );

    @Test
    public void shouldFindDenseNode() throws Throwable
    {
        try( Driver driver = GraphDatabase
                .driver( neo4j.boltURI() , Config.build().withoutEncryption().toConfig() ) )
        {
            // Given
            Session session = driver.session();
            session.run("CREATE (:Start)-[:REL]->(:Node{name:'middle'})-[:REL]->(d:Dense{name:'dense'}) " +
                    "WITH d " +
                    "UNWIND range(1,1000) as index " +
                    "CREATE (d)-[:REL]->(:Node{name:'index ' + index}) " +
                    "WITH distinct d " +
                    "CREATE (d)<-[:REL]-(:Node{name:'other'})");



            // When
            List<Record> results = session.run( "MATCH (s:Start) CALL expandTo.denseNodes.nodes(s, {degree:100}) yield node return node.name as name").list();

            // Then only return the first dense node
            assertThat(results.size(), equalTo(1));
            Record record = results.get(0);
            assertThat(record.get("name").asString(), equalTo( "dense" ) );
        }
    }

    @Test
    public void shouldFindPaths() throws Throwable
    {
        try( Driver driver = GraphDatabase
                .driver( neo4j.boltURI() , Config.build().withoutEncryption().toConfig() ) )
        {
            // Given
            Session session = driver.session();
            session.run("CREATE (s:Start)-[:REL]->(:Node{name:'middle'})-[:REL]->(d:Dense{name:'dense'}) " +
                    "CREATE (s)-[:REL]->(d)" +
                    "WITH distinct d " +
                    "UNWIND range(1,1000) as index " +
                    "CREATE (d)-[:REL]->(:Node{name:'index ' + index}) " +
                    "WITH distinct d " +
                    "CREATE (d)<-[:REL]-(:Node{name:'other'})");



            // When
            List<Record> results = session.run( "MATCH (s:Start) CALL expandTo.denseNodes.paths(s, {degree:100}) yield path with last(nodes(path)) as node, count(path) as count return node.name as name, count").list();

            // Then find both paths to the dense node
            assertThat(results.size(), equalTo(1));
            Record record = results.get(0);
                    assertThat( record.get("name").asString(), equalTo( "dense" ) );
            assertThat( record.get("count").asLong(), equalTo( 2l) );
        }
    }

    @Test
    public void shouldFindSinglePath() throws Throwable
    {
        try( Driver driver = GraphDatabase
                .driver( neo4j.boltURI() , Config.build().withoutEncryption().toConfig() ) )
        {
            // Given
            Session session = driver.session();
            session.run("CREATE (s:Start)-[:REL]->(:Node{name:'middle'})-[:REL]->(d:Dense{name:'dense'}) " +
                    "CREATE (s)-[:REL]->(d)" +
                    "WITH distinct d " +
                    "UNWIND range(1,1000) as index " +
                    "CREATE (d)-[:REL]->(:Node{name:'index ' + index}) " +
                    "WITH distinct d " +
                    "CREATE (d)<-[:REL]-(:Node{name:'other'})");



            // When
            List<Record> results = session.run( "MATCH (s:Start) CALL expandTo.denseNodes.singlePath(s, {degree:100}) yield path with last(nodes(path)) as node, count(path) as count return node.name as name, count").list();

            // Then find a single path to the dense node
            assertThat(results.size(), equalTo(1));
            Record record = results.get(0);
                    assertThat( record.get("name").asString(), equalTo( "dense" ) );
            assertThat( record.get("count").asLong(), equalTo( 1l) );
        }
    }

    @Test
    public void shouldNotFindWhenThresholdHigherThanDensity() throws Throwable
    {
        try( Driver driver = GraphDatabase
                .driver( neo4j.boltURI() , Config.build().withoutEncryption().toConfig() ) )
        {
            // Given
            Session session = driver.session();
            session.run("CREATE (:Start)-[:REL]->(:Node{name:'middle'})-[:REL]->(d:Dense{name:'dense'}) " +
                    "WITH d " +
                    "UNWIND range(1,1000) as index " +
                    "CREATE (d)-[:REL]->(:Node{name:'index ' + index}) " +
                    "WITH distinct d " +
                    "CREATE (d)<-[:REL]-(:Node{name:'other'})");



            // When
            List<Record> results = session.run( "MATCH (s:Start) CALL expandTo.denseNodes.nodes(s, {degree:10000}) yield node return node").list();

            // Then don't find anything, as no nodes have degree meeting threshold
            assertThat(results.size(), equalTo(0));
        }
    }

    @Test
    public void shouldFindWhenLookingForRelWithExplicitPattern() throws Throwable
    {
        try( Driver driver = GraphDatabase
                .driver( neo4j.boltURI() , Config.build().withoutEncryption().toConfig() ) )
        {
            // Given
            Session session = driver.session();
            session.run("CREATE (:Start)-[:REL]->(mid:Node{name:'middle'})-[:REL]->(d:Dense{name:'dense'}) " +
                    "WITH mid, d " +
                    "UNWIND range(1,1000) as index " +
                    "CREATE (d)-[:REL]->(:Node{name:'index ' + index}) " +
                    "WITH distinct mid, d " +
                    "CREATE (d)<-[:REL]-(:Node{name:'other'}) " +
                    "WITH mid " +
                    "UNWIND range(1,1000) as index " +
                    "CREATE (mid)<-[:REL]-(:Node)");



            // When
            List<Record> results = session.run( "MATCH (s:Start) CALL expandTo.denseNodes.nodes(s, {degree:100, denseRels:'REL>'}) yield node return node.name as name").list();

            // Then find only the dense node reachable by outgoing :REL relationships
            assertThat(results.size(), equalTo(1));
            Record record = results.get(0);
            assertThat( record.get("name").asString(), equalTo( "dense" ) );
        }
    }

    @Test
    public void shouldNotFindWhenLookingForRelWithWrongDirection() throws Throwable
    {
        try( Driver driver = GraphDatabase
                .driver( neo4j.boltURI() , Config.build().withoutEncryption().toConfig() ) )
        {
            // Given
            Session session = driver.session();
            session.run("CREATE (:Start)-[:REL]->(:Node{name:'middle'})-[:REL]->(d:Dense{name:'dense'}) " +
                    "WITH d " +
                    "UNWIND range(1,1000) as index " +
                    "CREATE (d)<-[:REL]-(:Node{name:'index ' + index}) " +
                    "WITH distinct d " +
                    "CREATE (d)<-[:REL]-(:Node{name:'other'})");



            // When
            List<Record> results = session.run( "MATCH (s:Start) CALL expandTo.denseNodes.nodes(s, {degree:100, denseRels:'REL>'}) yield node return node").list();

            // Then don't find anything, no dense nodes
            assertThat(results.size(), equalTo(0));
        }
    }

    @Test
    public void shouldNotContinueExpansionByDefault() throws Throwable
    {
        try( Driver driver = GraphDatabase
                .driver( neo4j.boltURI() , Config.build().withoutEncryption().toConfig() ) )
        {
            // Given
            Session session = driver.session();
            session.run("CREATE (:Start)-[:REL]->(mid:Node{name:'middle'})-[:REL]->(d:Dense{name:'dense'})-[:REL]->(d2:Dense{name:'dense2'}) " +
                    "WITH d, d2 " +
                    "UNWIND range(1,1000) as index " +
                    "CREATE (d)-[:REL]->(:Node{name:'index ' + index}) " +
                    "CREATE (d2)-[:REL]->(:Node{name:'index ' + index}) ");



            // When
            List<Record> results = session.run( "MATCH (s:Start) CALL expandTo.denseNodes.nodes(s, {degree:100, denseRels:'REL>'}) yield node return node.name as name").list();

            // Then only find first dense node, no expansion beyond
            assertThat(results.size(), equalTo(1));
            Record record = results.get(0);
            assertThat( record.get("name").asString(), equalTo( "dense" ) );
        }
    }

    @Test
    public void shouldContinueExpansionBeneathThreshold() throws Throwable
    {
        try( Driver driver = GraphDatabase
                .driver( neo4j.boltURI() , Config.build().withoutEncryption().toConfig() ) )
        {
            // Given
            Session session = driver.session();
            session.run("CREATE (:Start)-[:REL]->(mid:Node{name:'middle'})-[:REL]->(d:Dense{name:'dense'})-[:REL]->(d2:Dense{name:'dense2'})-[:REL]->(d3:Dense{name:'dense3'}) " +
                    "WITH d, d2, d3 " +
                    "UNWIND range(1,1000) as index " +
                    "CREATE (d)-[:REL]->(:Node{name:'index ' + index}) " +
                    "WITH distinct d2, d3 " +
                    "UNWIND range(1,1010) as index " +
                    "CREATE (d2)-[:REL]->(:Node{name:'2index ' + index}) " +
                    "WITH distinct d3 " +
                    "UNWIND range(1,1005) as index " +
                    "CREATE (d3)-[:REL]->(:Node{name:'3index ' + index}) ");



            // When
            List<Record> results = session.run( "MATCH (s:Start) CALL expandTo.denseNodes.nodes(s, {degree:100, denseRels:'REL>', continueBelow:1010}) yield node return node.name as name").list();

            // Then only find first 2 dense nodes, do not expand past second
            assertThat(results.size(), equalTo(2));
            Record record = results.get(0);
            assertThat( record.get("name").asString(), equalTo( "dense" ) );
            record = results.get(1);
            assertThat( record.get("name").asString(), equalTo( "dense2" ) );
        }
    }
}
