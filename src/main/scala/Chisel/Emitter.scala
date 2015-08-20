package Chisel

private class Emitter(circuit: Circuit) {
  override def toString = res.toString

  def emitDir(e: Data, isTop: Boolean): String =
    if (isTop) (if (e.isFlip) "input " else "output ")
    else (if (e.isFlip) "flip " else "")
  def emitPort(e: Data, isTop: Boolean): String =
    s"${emitDir(e, isTop)}${circuit.refMap(e).name} : ${e.toType}"
  private def emit(e: Command, ctx: Component): String = e match {
    case e: DefPrim[_] => s"node ${e.name} = ${e.op.name}(${e.args.map(_.fullName(ctx)).reduce(_+", "+_)})"
    case e: DefWire => s"wire ${e.name} : ${e.id.toType}"
    case e: DefRegister => s"reg ${e.name} : ${e.id.toType}, ${e.clock.fullName(ctx)}, ${e.reset.fullName(ctx)}"
    case e: DefMemory => s"cmem ${e.name} : ${e.id.toType}[${e.size}], ${e.clock.fullName(ctx)}";
    case e: DefSeqMemory => s"smem ${e.name} : ${e.id.toType}[${e.size}]";
    case e: DefAccessor => s"infer accessor ${e.name} = ${e.source.fullName(ctx)}[${e.index.fullName(ctx)}]"
    case e: Connect => s"${e.loc.fullName(ctx)} := ${e.exp.fullName(ctx)}"
    case e: BulkConnect => s"${e.loc1.fullName(ctx)} <> ${e.loc2.fullName(ctx)}"
    case e: ConnectInit => s"onreset ${e.loc.fullName(ctx)} := ${e.exp.fullName(ctx)}"
    case e: DefInstance => {
      val modName = moduleMap.getOrElse(e.id.name, e.id.name)
      val res = new StringBuilder(s"inst ${e.name} of $modName")
      res ++= newline
      for (p <- e.ports; x <- initPort(p, INPUT, ctx))
        res ++= newline + x
      res.toString
    }

    case w: WhenBegin =>
      indent()
      s"when ${w.pred.fullName(ctx)} :"
    case _: WhenElse =>
      indent()
      "else :"
    case _: WhenEnd =>
      unindent()
      "skip"
  }
  private def initPort(p: Data, dir: Direction, ctx: Component) = {
    for (x <- p.flatten; if x.dir == dir)
      yield s"${circuit.refMap(x).fullName(ctx)} := ${x.makeLit(0).name}"
  }

  private def emitBody(m: Component) = {
    val me = new StringBuilder
    withIndent {
      for (p <- m.ports)
        me ++= newline + emitPort(p, true)
      me ++= newline
      for (p <- m.ports; x <- initPort(p, OUTPUT, m))
        me ++= newline + x
      me ++= newline
      for (cmd <- m.commands)
        me ++= newline + emit(cmd, m)
      me ++= newline
    }
    me
  }

  private val bodyMap = collection.mutable.HashMap[StringBuilder, String]()
  private val moduleMap = collection.mutable.HashMap[String, String]()

  private def emit(m: Component): String = {
    val body = emitBody(m)
    bodyMap get body match {
      case Some(name) =>
        moduleMap(m.name) = name
        ""
      case None =>
        bodyMap(body) = m.name
        newline + s"module ${m.name} : " + body
    }
  }

  private var indentLevel = 0
  private def newline = "\n" + ("  " * indentLevel)
  private def indent(): Unit = indentLevel += 1
  private def unindent() { require(indentLevel > 0); indentLevel -= 1 }
  private def withIndent(f: => Unit) { indent(); f; unindent() }

  private val res = new StringBuilder(s"circuit ${circuit.name} : ")
  withIndent { circuit.components.foreach(c => res ++= emit(c)) }
  res ++= newline
}