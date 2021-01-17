package zio.magic.macros

import zio.{NonEmptyChunk, ZLayer}
import zio.prelude.Validation

import scala.reflect.macros.blackbox

case class ExprGraph[C <: blackbox.Context](graph: Graph[C#Expr[ZLayer[_, _, _]]], c: C) {
  def buildLayerFor(output: List[String]): C#Expr[ZLayer[_, _, _]] =
    try {
      graph.buildComplete(output) match {
        case Validation.Failure(errors) =>
          c.abort(c.enclosingPosition, renderErrors(errors))
        case Validation.Success(value) =>
          value
      }
    } catch {
      case e: StackOverflowError =>
        c.abort(c.enclosingPosition, "Circular Dependency in Layer Graph!")
    }

  private def renderErrors(errors: NonEmptyChunk[GraphError[C#Expr[ZLayer[_, _, _]]]]): String = {
    val errorMessage =
      errors
        .map(renderError)
        .mkString("\n")
        .linesIterator
        .mkString("\n🪄  ")
    val magicTitle = fansi.Color.Red("ZLayer Magic Missing Components").overlay(fansi.Underlined.On).toString()
    s"""
🪄  $magicTitle
🪄  $errorMessage

"""
  }

  private def renderError(error: GraphError[C#Expr[ZLayer[_, _, _]]]): String =
    error match {
      case GraphError.MissingDependency(node, dependency) =>
        val styledDependency = fansi.Color.White(dependency).overlay(fansi.Underlined.On)
        val styledLayer      = fansi.Color.White(node.value.tree.toString())
        s"""
provide $styledDependency
    for $styledLayer"""
      case GraphError.MissingTopLevelDependency(dependency) =>
        val styledDependency = fansi.Color.White(dependency).overlay(fansi.Underlined.On)
        s"""- $styledDependency"""
      case GraphError.CircularDependency(node, dependency) =>
        val styledDependency = fansi.Color.White(node.value.tree.toString()).overlay(fansi.Underlined.On)
        val styledLayer      = fansi.Color.White(dependency.value.tree.toString())
        s"""
${fansi.Color.Magenta("PARADOX ENCOUNTERED")} — Please don't open a rift in space-time!

$styledDependency
both requires ${fansi.Bold.On("and")} is transitively required by $styledLayer
    """
    }

}

object ExprGraph {
  def apply[C <: blackbox.Context](layers: List[Node[C#Expr[ZLayer[_, _, _]]]], c: C): ExprGraph[C] =
    ExprGraph[C](Graph(layers)(LayerLike.exprLayerLike(c).asInstanceOf[LayerLike[C#Expr[ZLayer[_, _, _]]]]), c)
}