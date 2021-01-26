/**
  * Copyright Whitebox Software Ltd. 2014
  * All Rights Reserved.
  *
  * Created by chris on 13/10/2016.
  *
  */
package io.venuu.vuu.murmur.consensus.zk

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.StrictLogging
import io.venuu.vuu.murmur.consensus.{PathCache, PathListener}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache._

import scala.util.{Failure, Success, Try}

class ZooKeeperPathCache(client: CuratorFramework) extends PathCache with StrictLogging with PathListener {

  val paths = new ConcurrentHashMap[String, TreeCache]()

  override def listeningTo: List[TreeCache] = {
    import scala.collection.JavaConversions._
    paths.elements().toList
  }

  override def connect(): PathCache = {
    //do nothing here
    this
  }

  override def createPath(path: String, data: Array[Byte]): PathCache = {
    Try(client.setData().forPath(path, data)) match {
      case Success(_) =>
      case Failure(err) =>
        client.create().creatingParentContainersIfNeeded().forPath(path, data)
    }
    this
  }


  override def createPath(path: String): PathCache = {
      createEmptyPath(path)
  }

  private def createEmptyPath(path: String): PathCache = {
    Try(client.setData().forPath(path)) match {
      case Success(_) =>
      case Failure(err) =>
        client.create().creatingParentContainersIfNeeded().forPath(path)
    }
    this
  }

  def listChildren(path: String): List[ChildData] = {

    import scala.collection.JavaConversions._

    val cache = paths.get(path)

    if(cache == null){

      createPath(path, Array())

      //listenTo(path)

      val cache = new TreeCache(client, path)

      cache.start()

      paths.put(path, cache)

      val children = cache.getCurrentChildren(path)

      if(children == null) List()
      else children.map(_._2).toList

//      cache.getCurrentChildren()
//
//      if(paths.get(path).getCurrentData.size() > 0)
//        paths.get(path).getCurrentData.toList
//      else
//        List()
//
    }
    else{
      cache.getCurrentChildren(path).map(_._2).toList
    }
  }

  override def updatePath(path: String, data: Array[Byte]): PathCache = {
    logger.info("updatePath " + path)
    client.setData().forPath(path, data)
    this
  }

  override def deletePath(path: String): PathCache = {
    logger.info("deletePath " + path)
    client.delete().forPath(path)
    this
  }

  override def listenTo(path: String): PathCache = {
    val cache = new TreeCache(client, path)
    cache.start()
    addListener(cache, this)
    paths.put(path, cache)
    this
  }


  override def listenTo(path: String, listener: PathListener): PathCache = {
    val cache = new TreeCache(client, path)
    cache.start()
    addListener(cache, listener)
    paths.put(path, cache)
    this
  }

  private def addListener(cache: TreeCache, pathListener: PathListener): Unit = {

    val listener = new TreeCacheListener {

      import TreeCacheEvent.Type._

      override def childEvent(client: CuratorFramework, event: TreeCacheEvent): Unit = {

        event.getType match {
          case NODE_ADDED => pathListener.onChildAdded(event.getData.getPath, event.getData.getData)
          case NODE_UPDATED => pathListener.onChildUpdated(event.getData.getPath, event.getData.getData)
          case NODE_REMOVED => pathListener.onChildRemoved(event.getData.getPath, event.getData.getData)
          case CONNECTION_SUSPENDED => pathListener.onConnectionSuspended()
          case CONNECTION_LOST => pathListener.onConnectionLost()
          case CONNECTION_RECONNECTED => pathListener.onConnectionReconnected()
          case INITIALIZED => pathListener.onInitialized()
          case _ => logger.warn("Got message don't know wht it is:" + event.toString)
        }
      }
    }

    cache.getListenable.addListener(listener)
  }


  override def disconnect(): Unit = {}

}