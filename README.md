## HowToGraph - GraphQL with Sangria and Neo4j Tutorial codebase

A code for GraphQL-Sangria Tutorial. Visit [GraphQL Scala Tutorial](https://www.howtographql.com/graphql-scala/0-introduction/) to learn more. This project is an extension of the tutorial with Neo4j implemented

### Running the example

```bash
sbt run
```

SBT will automatically compile and restart the server whenever the source code changes.

After the server is started you can run queries interactively using [GraphiQL](https://github.com/graphql/graphiql) by opening [http://localhost:8080](http://localhost:8080) in a browser.

Use different PORT if you've changed it int he configuration.

### Database Configuration

This example uses Neo4j [Neo4j](https://neo4j.com/) Graph database. The schema and example data will be deleted and re-created every time server starts.

For installing Neo4j [installation doc](https://neo4j.com/docs/operations-manual/current/installation/) is a great resource.

If you would like to change the database configuration or use a different database, then please update `src/main/resources/application.conf`.
