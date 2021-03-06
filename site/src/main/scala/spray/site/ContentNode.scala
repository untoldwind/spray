/*
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.site

import java.lang.{ StringBuilder => JStringBuilder }
import scala.xml.{Node, XML}
import spray.util._


sealed trait ContentNode {
  def title: String
  def name: String
  def uri: String
  def children: Seq[ContentNode]
  def isRoot: Boolean
  def parent: ContentNode
  def doc: SphinxDoc
  def post: BlogPost = doc.post.getOrElse(sys.error(s"$uri is not a blog-post"))
  def isLast = parent.children.last == this
  def isLeaf = children.isEmpty
  def level: Int = if (isRoot) 0 else parent.level + 1
  def absoluteUri = if (uri.startsWith("http") || uri.startsWith("/")) uri else "/" + uri
  def isDescendantOf(node: ContentNode): Boolean = node == this || !isRoot && parent.isDescendantOf(node)

  def find(uri: String): Option[ContentNode] =
    if (uri == this.uri) Some(this)
    else children.mapFind(_.find(uri))

  override def toString: String = {
    val sb = new JStringBuilder
    format(sb, "")
    sb.toString.dropRight(1)
  }

  private def format(sb: JStringBuilder, indent: String) {
    sb.append(indent).append(name).append(": ").append(uri).append("\n")
    children.foreach(_.format(sb, indent + "  "))
  }
}

class RootNode(val doc: SphinxDoc) extends ContentNode {
  val children: Seq[ContentNode] = (XML.loadString(doc.body) \ "ul" \ "li").par.map(li2Node(this)).seq
  def title = "HTTP and more for your Akka/Scala Actors"
  def name = "root"
  def uri = ""
  def isRoot = true
  def parent = this

  private def li2Node(_parent: ContentNode)(li: Node): ContentNode =
    new ContentNode {
      import SphinxDoc.load
      val a = (li \ "a").head
      def title = if (parent.isRoot) name else parent.title + " » " + name
      val name = a.text
      val uri = (a \ "@href").text
      val children: Seq[ContentNode] = (li \ "ul" \ "li").map(li2Node(this))(collection.breakOut)
      var lastDoc: Option[SphinxDoc] = None
      def doc: SphinxDoc = lastDoc.getOrElse {
        val loaded =
          if (uri.contains("#")) SphinxDoc.Empty
          else load(uri).orElse(load(uri + "index/")).getOrElse(sys.error(s"SphinxDoc for uri '$uri' not found"))
        if (!SiteSettings.DevMode) lastDoc = Some(loaded)
        loaded
      }
      def isRoot = false
      def parent = _parent
    }
}