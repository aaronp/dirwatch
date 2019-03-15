package dirscan


sealed trait Diff[A]

object Diff {

  implicit object DiffFunctor extends cats.Functor[Diff] {
    override def map[A, B](fa: Diff[A])(f: A => B): Diff[B] = {
      fa match {
        case Removed(a) => Removed(f(a))
        case Modified(a) => Modified(f(a))
      }
    }
  }

}

case class Removed[A](value: A) extends Diff[A]

case class Modified[A](value: A) extends Diff[A]

