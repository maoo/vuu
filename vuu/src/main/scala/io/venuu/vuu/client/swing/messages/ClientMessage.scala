/**
  * Copyright Whitebox Software Ltd. 2014
  * All Rights Reserved.

  * Created by chris on 06/01/2016.

  */
package io.venuu.vuu.client.swing.messages

import java.util.UUID

import io.venuu.toolbox.time.TimeProvider
import io.venuu.vuu.client.swing.client.UserPrincipal
import io.venuu.vuu.net.{FilterSpec, SortSpec}

object RequestId{
  def oneNew()(implicit timeProvider: TimeProvider) = {
    "REQ-" + UUID.randomUUID().toString
  }
}

trait ClientMessage

trait FromServer
trait ToServer

case class Logon(user: String, password: String) extends ClientMessage with ToServer
case class LogonSuccess(body: UserPrincipal) extends ClientMessage with FromServer
case class LogonFailure(err: String) extends ClientMessage with FromServer

case class ClientCreateViewPort(requestId: String, table: String, columns: Array[String], sortBy: SortSpec, groupBy: Array[String], from: Int, to: Int, filter: String) extends ClientMessage  with ToServer
case class ClientCreateViewPortSuccess(requestId: String, vpId: String, columns: Array[String], sortBy: SortSpec, groupBy: Array[String], filter: String) extends ClientMessage with FromServer
case class ClientCreateViewPortFailure(requestId: String, vpId: String, error: String) extends ClientMessage with FromServer

case class ClientChangeViewPortRequest(requestId: String, viewPortId: String, columns: Array[String], sortBy: SortSpec = SortSpec(List()), groupBy: Array[String] = Array(), filterSpec: FilterSpec = null) extends ClientMessage with ToServer
case class ClientChangeViewPortSuccess(requestId: String, viewPortId: String, columns: Array[String], sortBy: SortSpec = SortSpec(List()), groupBy: Array[String] = Array(), filterSpec: FilterSpec = null) extends ClientMessage with FromServer
case class ClientChangeViewPortFailure(requestId: String, viewPortId: String, columns: Array[String], sortBy: SortSpec = SortSpec(List()), groupBy: Array[String] = Array(), filterSpec: FilterSpec = null) extends ClientMessage with FromServer

case class ClientUpdateVPRange(requestId: String, vpId: String, from: Int, to: Int) extends ClientMessage with ToServer
case class ClientServerRowUpdate(vpId: String, index: Int, data: Array[AnyRef], size: Int) extends ClientMessage with FromServer

case class ClientGetTableList(requestId: String) extends ClientMessage
case class ClientGetTableListResponse(requestId: String, tables: Array[String]) extends ClientMessage

case class ClientGetTableMeta(requestId: String, table: String) extends ClientMessage
case class ClientGetTableMetaResponse(requestId: String, table: String, columns: Array[String], dataTypes: Array[String], key: String) extends ClientMessage

case class ClientRpcTableUpdate(requestId: String, table: String, key: String, data: Map[String, Any]) extends ClientMessage
case class ClientRpcTableUpdateSuccess(requestId: String, table: String, key: String, data: Map[String, Any]) extends ClientMessage

case class ClientOpenTreeNodeRequest(requestId: String, vpId: String, treeKey: String) extends ClientMessage with ToServer
case class ClientCloseTreeNodeRequest(requestId: String, vpId: String, treeKey: String) extends ClientMessage with ToServer