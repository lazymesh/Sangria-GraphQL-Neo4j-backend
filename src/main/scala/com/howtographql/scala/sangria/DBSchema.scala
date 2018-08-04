package com.howtographql.scala.sangria

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import org.neo4j.driver.v1.{AuthTokens, Driver, GraphDatabase}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps


object DBSchema extends StrictLogging {
  val config = ConfigFactory.load()

  /**
    * Load schema and populate sample data withing this Sequence od DBActions
    */
  def databaseSetup(connection: Driver) = {
    def deleteLinkNode = {
      val session = connection.session()
      val deleteString = "MATCH (n: Link) DELETE n"
      val query = s""" $deleteString""".stripMargin

      session.runAsync(query).whenComplete((_, _) => session.closeAsync())
    }

    def createLinkNode = {
      val session = connection.session()

      val createString = "UNWIND [{id: 1, url: \"http://howtographql.com\", description: \"Awesome community driven GraphQL tutorial\", postedBy:1, createdAt: localdatetime({date:date({ year:2018, month:07, day:03 }), time: localtime({ hour:12, minute:31, second:14, millisecond: 000 })})}," +
        "{id: 2, url: \"http://graphql.org\", description:\"Official GraphQL web page\", postedBy:2, createdAt: localdatetime({date:date({ year:2018, month:06, day:11 }), time: localtime({ hour:12, minute:31, second:00, millisecond: 000 })})}," +
        "{id: 3, url: \"https://facebook.github.io/graphql/\", description: \"GraphQL specification\", postedBy:1, createdAt: localdatetime({date:date({ year:2018, month:10, day:12 }), time: localtime({ hour:12, minute:31, second:14, millisecond: 000 })})}] as links"

      val query = s"""$createString CREATE (n: Link) SET n=links return n""".stripMargin

      session.runAsync(query).whenComplete((_, _) => session.closeAsync())
    }
    def deleteUserNode = {
      val session = connection.session()
      val deleteString = "MATCH (n: User) DELETE n"
      val query = s""" $deleteString""".stripMargin

      session.runAsync(query).whenComplete((_, _) => session.closeAsync())
    }

    def createUserNode = {
      val session = connection.session()

      val createString = "UNWIND [{id: 1, name: \"Sam Maharjan\", email: \"sam@gmail.com\", password:\"1234rt\", createdAt: localdatetime({date:date({ year:2018, month:07, day:03 }), time: localtime({ hour:12, minute:31, second:14})})}," +
        "{id: 2, name: \"Lam Dangol\", email:\"lam@gmail.com\", password: \"1234tr\", createdAt: localdatetime({date:date({ year:2018, month:06, day:11 }), time: localtime({ hour:12, minute:31, second:00})})}] as users"

      val query = s"""$createString CREATE (u: User) SET u=users return u""".stripMargin

      session.runAsync(query).whenComplete((_, _) => session.closeAsync())
    }
    def deleteVoteNode = {
      val session = connection.session()
      val deleteString = "MATCH (n: Vote) DELETE n"
      val query = s""" $deleteString""".stripMargin

      session.runAsync(query).whenComplete((_, _) => session.closeAsync())
    }

    def createVodeNode = {
      val session = connection.session()

      val createString = "UNWIND [{id: 1, createdAt: localdatetime({date:date({ year:2018, month:07, day:03 }), time: localtime({ hour:12, minute:31, second:14})}), userId: 1, linkId: 2}," +
        "{id: 2, createdAt: localdatetime({date:date({ year:2018, month:06, day:11 }), time: localtime({ hour:12, minute:31, second:00})}), userId: 2, linkId:1}," +
        "{id: 3, createdAt: localdatetime({date:date({ year:2018, month:10, day:12 }), time: localtime({ hour:12, minute:31, second:14})}), userId:1, linkId: 3}] as votes"

      val query = s"""$createString CREATE (n: Vote) SET n=votes return n""".stripMargin

      session.runAsync(query).whenComplete((_, _) => session.closeAsync())
    }
    deleteLinkNode
    createLinkNode
    deleteUserNode
    createUserNode
    deleteVoteNode
    createVodeNode
  }


  def createDatabase: DAO = {
    implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global
    val neo4jConnection = createNeo4jConnection()
    databaseSetup(neo4jConnection)
    new DAO(neo4jConnection)
  }

  /**
    * The problem, that it takes ~15 seconds for Neo4j to initialize all internal services in a docker container.
    * So, without the retry policy the test will fail due to missing connection with Neo4j instance.
    */
  private def createNeo4jConnection()(implicit s: Scheduler): Driver = {
    logger.info(s"Creating Neo4j connection with config ${config.getConfig("neo4j")}")
    println(s"Creating Neo4j connection with config ${config.getConfig("neo4j")}")

    import scala.concurrent.duration._

    def connectionAttempt() = Task {
      GraphDatabase.driver(config.getString("neo4j.uri"), AuthTokens.basic(config.getString("neo4j.username"), config.getString("neo4j.password")))
    }

    val result = retry("Acquire Neo4j connection", connectionAttempt(), 5.seconds, 10.seconds, 10)

    Await.result(result.runAsync, 55.seconds)
  }

  private def retry[T](name: String, originalTask: => Task[T], delay: FiniteDuration, timeout: FiniteDuration, retries: Int): Task[T] = {
    val delayedTask = originalTask.delayExecution(delay).timeout(timeout)

    def loop(task: Task[T], retries: Int): Task[T] = {
      task.onErrorRecoverWith { case error if retries > 0 =>
        logger.error(s"[$name] Retry policy. Current retires [$retries]. Delay [$delay]. Error [${error.getMessage}]")
        loop(task, retries - 1)
      }
    }

    loop(delayedTask, retries)
  }

}
