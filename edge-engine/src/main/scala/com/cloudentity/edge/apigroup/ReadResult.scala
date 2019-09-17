package com.cloudentity.edge.apigroup

sealed trait ReadResult[A] {
  def map[B](f: A => B): ReadResult[B] =
    this match {
      case ValidResult(path, value) => ValidResult(path, f(value))
      case InvalidResult(path, msg) => InvalidResult[B](path, msg)
    }

  def asValid(): Option[ValidResult[A]] =
    this match {
      case x: ValidResult[A] => Some(x)
      case _                 => None
    }

  def asInvalid(): Option[InvalidResult[A]] =
    this match {
      case x: InvalidResult[A] => Some(x)
      case _                   => None
    }
}

case class ValidResult[A](path: List[String], value: A) extends ReadResult[A]
case class InvalidResult[A](path: List[String], msg: String) extends ReadResult[A]