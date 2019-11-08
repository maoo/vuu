/**
  * Copyright Whitebox Software Ltd. 2014
  * All Rights Reserved.
  *
  * Created by chris on 07/09/2016.
  *
  */
package io.venuu.vuu.viewport

import io.venuu.toolbox.jmx.{MetricsProvider, MetricsProviderImpl}
import io.venuu.toolbox.lifecycle.LifecycleContainer
import io.venuu.toolbox.time.DefaultTimeProvider
import io.venuu.vuu.core.table.RowWithData
import io.venuu.vuu.net.{ClientSessionId, FilterSpec, SortSpec}
import io.venuu.vuu.provider.{JoinTableProvider, MockProvider}
import io.venuu.vuu.util.OutboundRowPublishQueue
import io.venuu.vuu.util.table.TableAsserts._
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.prop.Tables.Table
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class GroupByAndAggregate2Test extends FeatureSpec with Matchers with GivenWhenThen {

  import io.venuu.vuu.core.table.TableTestHelper._
  import io.venuu.vuu.viewport.OrdersAndPricesScenarioFixture._

  val dateTime = new DateTime(2015, 7, 24, 11, 0, DateTimeZone.forID("Europe/London")).toDateTime.toInstant.getMillis

  def runContainersOnce(viewPortContainer: ViewPortContainer, joinProvider: JoinTableProvider) = {
    joinProvider.runOnce()
    viewPortContainer.runOnce()
    viewPortContainer.runGroupByOnce()
  }

  def tickInData(ordersProvider: MockProvider, pricesProvider: MockProvider): Unit = {
    ordersProvider.tick("NYC-0001", Map("orderId" -> "NYC-0001", "trader" -> "chris", "tradeTime" -> dateTime, "quantity" -> 100, "ric" -> "VOD.L"))
    ordersProvider.tick("NYC-0002", Map("orderId" -> "NYC-0002", "trader" -> "chris", "tradeTime" -> dateTime, "quantity" -> 200, "ric" -> "VOD.L"))
    ordersProvider.tick("NYC-0003", Map("orderId" -> "NYC-0003", "trader" -> "chris", "tradeTime" -> dateTime, "quantity" -> 300, "ric" -> "VOD.L"))
    ordersProvider.tick("NYC-0004", Map("orderId" -> "NYC-0004", "trader" -> "chris", "tradeTime" -> dateTime, "quantity" -> 400, "ric" -> "VOD.L"))
    ordersProvider.tick("NYC-0005", Map("orderId" -> "NYC-0005", "trader" -> "chris", "tradeTime" -> dateTime, "quantity" -> 500, "ric" -> "VOD.L"))
    ordersProvider.tick("NYC-0006", Map("orderId" -> "NYC-0006", "trader" -> "steve", "tradeTime" -> dateTime, "quantity" -> 600, "ric" -> "VOD.L"))
    ordersProvider.tick("NYC-0007", Map("orderId" -> "NYC-0007", "trader" -> "steve", "tradeTime" -> dateTime, "quantity" -> 1000, "ric" -> "BT.L"))
    ordersProvider.tick("NYC-0008", Map("orderId" -> "NYC-0008", "trader" -> "steve", "tradeTime" -> dateTime, "quantity" -> 500, "ric" -> "BT.L"))

    pricesProvider.tick("VOD.L", Map("ric" -> "VOD.L", "bid" -> 220.0, "ask" -> 222.0))
    pricesProvider.tick("BT.L", Map("ric" -> "BT.L", "bid" -> 500.0, "ask" -> 501.0))
  }

  scenario("test groupBy tree structure update") {

    implicit val lifeCycle = new LifecycleContainer
    implicit val timeProvider = new DefaultTimeProvider
    implicit val metrics: MetricsProvider = new MetricsProviderImpl

    val (joinProvider, orders, prices, orderPrices, ordersProvider, pricesProvider, viewPortContainer) = setup()

    joinProvider.start()

    tickInData(ordersProvider, pricesProvider)

    joinProvider.runOnce()

    val queue = new OutboundRowPublishQueue()
    val highPriorityQueue = new OutboundRowPublishQueue()

    val columns = orderPrices.getTableDef.columns

    val viewport = viewPortContainer.create(ClientSessionId("A", "B"),
      queue, highPriorityQueue, orderPrices, ViewPortRange(0, 20), columns.toList,
      SortSpec(List()),
      FilterSpec(""),
      GroupBy(orderPrices, "trader", "ric")
        .withSum("quantity")
        .withCount("trader")
        .asClause()
    )

    //expect nothing
    viewPortContainer.runOnce()

    viewport.combinedQueueLength should be(0) //as have no keys

    viewPortContainer.openNode(viewport.id, "$root/chris")
    viewPortContainer.openNode(viewport.id, "$root/chris/VOD.L")
    viewPortContainer.openNode(viewport.id, "$root/steve")
    viewPortContainer.closeNode(viewport.id, "$root/steve/BT.L")
    
    viewPortContainer.runOnce()
    viewPortContainer.runGroupByOnce()

    //expect realised rows
    viewPortContainer.runOnce()
    ordersProvider.tick("NYC-0008", Map("orderId" -> "NYC-0008", "trader" -> "chris", "tradeTime" -> dateTime, "quantity" -> 700, "ric" -> "BT.L"))

    viewPortContainer.runOnce()
    viewPortContainer.runGroupByOnce()

    //viewport.combinedQueueLength should be(14) //as have no keys

    val updates = combineQs(viewport)

    assertVpEq(updates) {

      Table(
        ("_isOpen" ,"_depth"  ,"_treeKey","_isLeaf" ,"_childCount","_caption","orderId" ,"trader"  ,"ric"     ,"tradeTime","quantity","bid"     ,"ask"     ,"last"    ,"open"    ,"close"   ),
        (true      ,0         ,"$root"   ,false     ,2         ,""        ,""        ,"[2]"     ,""        ,""        ,"Sum: 3800.0",""        ,""        ,""        ,""        ,""        ),
        (true      ,1         ,"$root/chris",false     ,2         ,"chris"   ,""        ,"[1]"     ,""        ,""        ,"Sum: 2200.0",""        ,""        ,""        ,""        ,""        ),
        (true      ,2         ,"$root/chris/VOD.L",false     ,5         ,"VOD.L"   ,""        ,"[1]"     ,"VOD.L"   ,""        ,"Sum: 1500.0",""        ,""        ,""        ,""        ,""        ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0001",true      ,0         ,"NYC-0001","NYC-0001","chris"   ,"VOD.L"   ,1437732000000l,100       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0002",true      ,0         ,"NYC-0002","NYC-0002","chris"   ,"VOD.L"   ,1437732000000l,200       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0003",true      ,0         ,"NYC-0003","NYC-0003","chris"   ,"VOD.L"   ,1437732000000l,300       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0004",true      ,0         ,"NYC-0004","NYC-0004","chris"   ,"VOD.L"   ,1437732000000l,400       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0005",true      ,0         ,"NYC-0005","NYC-0005","chris"   ,"VOD.L"   ,1437732000000l,500       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (true      ,1         ,"$root/steve",false     ,2         ,"steve"   ,""        ,"[1]"     ,""        ,""        ,"Sum: 1600.0",""        ,""        ,""        ,""        ,""        ),
        (false     ,2         ,"$root/steve/VOD.L",false     ,1         ,"VOD.L"   ,""        ,"[1]"     ,"VOD.L"   ,""        ,"Sum: 600.0",""        ,""        ,""        ,""        ,""        ),
        (false     ,2         ,"$root/steve/BT.L",false     ,1         ,"BT.L"    ,""        ,"[1]"     ,"BT.L"    ,""        ,"Sum: 1000.0",""        ,""        ,""        ,""        ,""        ),
        (false     ,2         ,"$root/chris/BT.L",false     ,1         ,"BT.L"    ,""        ,"[1]"     ,"BT.L"    ,""        ,"Sum: 700.0",""        ,""        ,""        ,""        ,""        )
      )

    }

    Given("A change in the structure of the tree (i.e. records reassigned to a new parent)")

    ordersProvider.tick("NYC-0001", Map("orderId" -> "NYC-0001", "trader" -> "steve"))
    ordersProvider.tick("NYC-0002", Map("orderId" -> "NYC-0002", "trader" -> "steve"))
    ordersProvider.tick("NYC-0003", Map("orderId" -> "NYC-0003", "trader" -> "steve"))
    ordersProvider.tick("NYC-0004", Map("orderId" -> "NYC-0004", "trader" -> "steve"))

    viewPortContainer.openNode(viewport.id, "$root/steve/VOD.L")

    viewPortContainer.runOnce()

    viewPortContainer.runGroupByOnce()

    val updates2 = combineQs(viewport)

    Then("check after we update the tree, the updates are visible ")

    assertVpEq(updates2) {
      Table(
        ("_isOpen" ,"_depth"  ,"_treeKey","_isLeaf" ,"_childCount","_caption","orderId" ,"trader"  ,"ric"     ,"tradeTime","quantity","bid"     ,"ask"     ,"last"    ,"open"    ,"close"   ),
        (true      ,1         ,"$root/steve",false     ,2         ,"steve"   ,""        ,"[1]"     ,""        ,""        ,"Sum: 2600.0",""        ,""        ,""        ,""        ,""        ),
        (true      ,2         ,"$root/steve/VOD.L",false     ,5         ,"VOD.L"   ,""        ,"[1]"     ,"VOD.L"   ,""        ,"Sum: 1600.0",""        ,""        ,""        ,""        ,""        ),
        (false     ,3         ,"$root/steve/VOD.L/NYC-0001",true      ,0         ,"NYC-0001","NYC-0001","steve"   ,"VOD.L"   ,1437732000000l,100       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/steve/VOD.L/NYC-0002",true      ,0         ,"NYC-0002","NYC-0002","steve"   ,"VOD.L"   ,1437732000000l,200       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/steve/VOD.L/NYC-0003",true      ,0         ,"NYC-0003","NYC-0003","steve"   ,"VOD.L"   ,1437732000000l,300       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/steve/VOD.L/NYC-0004",true      ,0         ,"NYC-0004","NYC-0004","steve"   ,"VOD.L"   ,1437732000000l,400       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/steve/VOD.L/NYC-0006",true      ,0         ,"NYC-0006","NYC-0006","steve"   ,"VOD.L"   ,1437732000000l,600       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,2         ,"$root/steve/BT.L",false     ,1         ,"BT.L"    ,""        ,"[1]"     ,"BT.L"    ,""        ,"Sum: 1000.0",""        ,""        ,""        ,""        ,""        ),
        (true      ,1         ,"$root/chris",false     ,2         ,"chris"   ,""        ,"[1]"     ,""        ,""        ,"Sum: 1200.0",""        ,""        ,""        ,""        ,""        ),
        (true      ,2         ,"$root/chris/VOD.L",false     ,1         ,"VOD.L"   ,""        ,"[1]"     ,"VOD.L"   ,""        ,"Sum: 500.0",""        ,""        ,""        ,""        ,""        ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0005",true      ,0         ,"NYC-0005","NYC-0005","chris"   ,"VOD.L"   ,1437732000000l,500       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,2         ,"$root/chris/BT.L",false     ,1         ,"BT.L"    ,""        ,"[1]"     ,"BT.L"    ,""        ,"Sum: 700.0",""        ,""        ,""        ,""        ,""        )
      )
    }

  }

  scenario("test groupBy with source table update") {

    implicit val lifeCycle = new LifecycleContainer
    implicit val timeProvider = new DefaultTimeProvider
    implicit val metrics: MetricsProvider = new MetricsProviderImpl

    val (joinProvider, orders, prices, orderPrices, ordersProvider, pricesProvider, viewPortContainer) = setup()

    joinProvider.start()

    tickInData(ordersProvider, pricesProvider)

    joinProvider.runOnce()

    val queue = new OutboundRowPublishQueue()
    val highPriorityQueue = new OutboundRowPublishQueue()

    val columns = orderPrices.getTableDef.columns

    val viewport = viewPortContainer.create(ClientSessionId("A", "B"),
      queue, highPriorityQueue, orderPrices, ViewPortRange(0, 20), columns.toList,
      SortSpec(List()),
      FilterSpec(""),
      GroupBy(orderPrices, "trader", "ric")
        .withSum("quantity")
        .withCount("trader")
        .asClause()
    )

    //expect nothing
    viewPortContainer.runOnce()

    viewport.combinedQueueLength should be(0) //as have no keys

    viewPortContainer.openNode(viewport.id, "$root/chris")
    viewPortContainer.openNode(viewport.id, "$root/chris/VOD.L")
    //      viewPortContainer.openNode(viewport.id, "$root/steve")
    //      viewPortContainer.openNode(viewport.id, "$root/steve/BT.L")

    viewPortContainer.runOnce()

    viewPortContainer.runGroupByOnce()

    //expect realised rows
    viewPortContainer.runOnce()

    ordersProvider.tick("NYC-0008", Map("orderId" -> "NYC-0008", "trader" -> "chris", "tradeTime" -> dateTime, "quantity" -> 700, "ric" -> "BT.L"))

    viewPortContainer.runOnce()

    viewPortContainer.runGroupByOnce()

    //viewport.combinedQueueLength should be(14) //as have no keys

    val updates = combineQs(viewport)

    val arraysOfMaps = updates.filter(vpu => vpu.vpUpdate == RowUpdateType).map(vpu => vpu.table.pullRow(vpu.key.key, vpu.vp.getColumns).asInstanceOf[RowWithData].data).toArray

    assertVpEq(updates) {
      Table(
        ("_isOpen" ,"_depth"  ,"_treeKey","_isLeaf" ,"_childCount","_caption","orderId" ,"trader"  ,"ric"     ,"tradeTime","quantity","bid"     ,"ask"     ,"last"    ,"open"    ,"close"   ),
        (true      ,0         ,"$root"   ,false     ,2         ,""        ,""        ,"[2]"     ,""        ,""        ,"Sum: 3800.0",""        ,""        ,""        ,""        ,""        ),
        (true      ,1         ,"$root/chris",false     ,2         ,"chris"   ,""        ,"[1]"     ,""        ,""        ,"Sum: 2200.0",""        ,""        ,""        ,""        ,""        ),
        (true      ,2         ,"$root/chris/VOD.L",false     ,5         ,"VOD.L"   ,""        ,"[1]"     ,"VOD.L"   ,""        ,"Sum: 1500.0",""        ,""        ,""        ,""        ,""        ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0001",true      ,0         ,"NYC-0001","NYC-0001","chris"   ,"VOD.L"   ,1437732000000l,100       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0002",true      ,0         ,"NYC-0002","NYC-0002","chris"   ,"VOD.L"   ,1437732000000l,200       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0003",true      ,0         ,"NYC-0003","NYC-0003","chris"   ,"VOD.L"   ,1437732000000l,300       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0004",true      ,0         ,"NYC-0004","NYC-0004","chris"   ,"VOD.L"   ,1437732000000l,400       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,3         ,"$root/chris/VOD.L/NYC-0005",true      ,0         ,"NYC-0005","NYC-0005","chris"   ,"VOD.L"   ,1437732000000l,500       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (true      ,1         ,"$root/steve",false     ,2         ,"steve"   ,""        ,"[1]"     ,""        ,""        ,"Sum: 1600.0",""        ,""        ,""        ,""        ,""        ),
        (true      ,2         ,"$root/steve/VOD.L",false     ,1         ,"VOD.L"   ,""        ,"[1]"     ,"VOD.L"   ,""        ,"Sum: 600.0",""        ,""        ,""        ,""        ,""        ),
        (false     ,3         ,"$root/steve/VOD.L/NYC-0006",true      ,0         ,"NYC-0006","NYC-0006","steve"   ,"VOD.L"   ,1437732000000l,600       ,220.0     ,222.0     ,null      ,null      ,null      ),
        (false     ,2         ,"$root/steve/BT.L",false     ,1         ,"BT.L"    ,""        ,"[1]"     ,"BT.L"    ,""        ,"Sum: 1000.0",""        ,""        ,""        ,""        ,""        ),
        (false     ,2         ,"$root/chris/BT.L",false     ,1         ,"BT.L"    ,""        ,"[1]"     ,"BT.L"    ,""        ,"Sum: 700.0",""        ,""        ,""        ,""        ,""        )
      )
    }

  }

}