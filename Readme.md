# What is CheckGraph? 
CheckGraph is a Scala library that provides a Free Monad based DSL for veryifying the correcness of Neo4j graphs. It is intended to be used as an integration testing tool, and its performance on very large graphs would currently be poor.

# Why use CheckGraph? 
While there is already GraphUnit, a Neo4j unit-testing framework written in Java, CheckGraph provides features that enable the writing of more comprehensive tests.

While a GraphUnit test consists of a query whose result will be evaluated against the expected output, such a test case can easily ways in which the actual output differs from the expected output.

For example, a test query may due to oversight omit parts of a subgraph to be created. Even if it covers all parts of a subgraph to be created, it will probably not detect any additional nodes or edges that weren't meant to be created.

Given a query like this:
```
MATCH (user :User { userId: 132 }) -[:Created]-> (item :Item)
RETURN user, item
```
we would not spot a bug which would cause an item to be 'created' by multiple users, but this would very likely cause issues with our queries (and information to be lost).
Neo4j's schemaless nature makes it very easy for errors such as this to creep in.

CheckGraph test cases are not based on queries meant to capture the structure of the expected graph. Rather, the CheckGraph DSL allows for test cases to exactly describe the whole graph's structure down to every expected vertex and edge. Hence, any anomalies, whether they involve missing or extra edges or vertices, will be detected. 

# Usage
A CheckGraph test definition involves in using the provided Free Monad DSL to build a `Free[DslCommand, S]` (type-aliased as type `CheckProgram[S]`). This program is then compiled down to a sequence of Neo4j queries which capture parts of the graph. Any match errors (a vertex or edge not being found) as well as any extra elements (elements present in the graph but not the definition in CheckProgram) are reported as errors.

## Matching a vertex
```
vertex(
    labels = Set("LabelA", "LabelB"), 
    attributes = Map("vid" -> N4jUid("RealUidHere"))
)
```

The above demonstrates matching a single vertex which has `LabelA` and `LabelB` and an attribute `vid` which has a uid value of `RealUidHere`. 

Vertex matches MUST return a single vertex; no matches or more than one match will both be reported as errors.

A vertex match will return a `MatchVertex` which can be used in subsequent path matching statements.

>> It's not currently possible to require a vertex to have exactly the labels or attributes provided. A matched vertex may have other labels not included in the `labels` set, or other attributes not included in `attributes`.

## Matching a path
```
v1      <- vertex(Set("A"), Map("uid" -> N4jUid(aUid)))
v2      <- vertex(Set("B"), Map("uid" -> N4jUid(bUid)))
path    <- edge(v1, v2, Set("RELATES_TO"))
```

The above snippet matches a path from vertex `v1` to vertex `v2` via an edge with the label `RELATES_TO`.

The path query (created via `edge`) returns a list of matched vertices (in this case of length 2). While it's possible to specify a path this way, it's a bit cumbersome. Here's the equivalent match using a bit of DSL magic:

```
path <- vertex(Set("A"), Map("uid" -> N4jUid(aUid))) -"RELATES_TO_AB"->
     vertex(Set("B"), Map("uid" -> N4jUid(bUid)))
(v1, v2) = (path.vertices(0), path.vertices(1))
```

Here the vertices and the edge are matched in a single DSL statement; the optional second statement retrieves the 
matched vertices from the returned path.

## Test isolation
Since CheckGraph analyses the entire graph, executing test cases in parallel is problematic since the exact structure of the graph will differ depending on what tests are running or have run.

One way to achieve isolation would be to run all tests sequentially and to clear out and rebuild the database before each test case. This would be slow both because of the sequential run order and due to time taken resetting the database for each test.

CheckGraph achieves isolation via a `graphLabel` which all nodes belonging to the graph share. CheckGraph will automatically add that label to any queries it makes, meaning that by using unique (randomly generated for optimal isolation) graph labels each test case can operate on its own unique subgraph without interference from other test cases.

# Test setup
Unfortunately the current version of Scala doesn't play nicely with embedded Neo4j and hence the tests require an
external instance of Neo4j to run. The instance must run on localhost with its bolt port being the default `7687`; 
its login credentials must be username `neo4j` and password `test`.

# Limitations
While CheckGraph allows matching edges with a set of labels (as with nodes), it does not currently allow the user to specify properties on edges.

The syntax for expressing relationships is also not yet quite as powerful as Cypher's, meaning some path queries 

# License
MIT Copyright (c) 2017-2019 Children's Medical Research Institute (CMRI)
