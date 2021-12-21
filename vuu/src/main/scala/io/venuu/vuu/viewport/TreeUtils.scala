package io.venuu.vuu.viewport

import io.venuu.toolbox.collection.array.ImmutableArray

object TreeUtils {

  def isDiff(oldNode: TreeNode, newNode: TreeNode): Boolean = {
    true
  }

  def notLeafOrRoot(node: TreeNode): Boolean = {
    ! node.isRoot && ! node.isLeaf
  }

  def diffOldVsNewBranches(oldTree: Tree, newTree: Tree): ImmutableArray[String] = {
    val arr = oldTree.nodes().filter(notLeafOrRoot).filter( oldNode => {
      newTree.getNode(oldNode.key) match {
        case newNode: TreeNode =>
          true
        case null =>
          //old node no longer there
          false
      }
    } ).map(_.key).toArray

    ImmutableArray.from(arr)
  }


}
