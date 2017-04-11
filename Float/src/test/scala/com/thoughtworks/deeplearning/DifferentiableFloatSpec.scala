package com.thoughtworks.deeplearning

import org.scalatest._
import Float._
import com.thoughtworks.deeplearning.Tape.{Aux, Literal}
import Poly._
import com.thoughtworks.deeplearning.Float.Optimizers.{LearningRate, Optimizer}
import com.thoughtworks.deeplearning.Float.WeightOps
import com.thoughtworks.raii.{RAIIFuture, RAIITask, RAIITask2, ResourceFactoryT}
import com.thoughtworks.deeplearning.TapeTaskFactory.floatToCompute
import com.thoughtworks.deeplearning.TapeTaskFactory.toRAIITask
import com.thoughtworks.deeplearning.Float.RAIIOps

import scala.concurrent.Promise
import scalaz.concurrent.{Future, Task}
import com.thoughtworks.each.Monadic._
import com.thoughtworks.raii.ResourceFactoryT.ResourceT
import shapeless.Lazy

import scala.annotation.tailrec
import scalaz.{-\/, \/, \/-}

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
final class DifferentiableFloatSpec extends AsyncFreeSpec with Matchers with Inside {

  "Plus" in {

    implicit def optimizer: Optimizer = new LearningRate {
      def currentLearningRate() = 1.0f
    }

    val weight: Weight = 1.0f.toWeight

    implicit def weightTaskToTapeTask(w: Weight): RAIITask2[Tape.Aux[Float, Float]] = RAIITask.unmanaged(w)

    implicit def tapeToTape[A](w: A)(
        implicit constraint: Lazy[A => Tape.Aux[Float, Float]]
    ): RAIITask2[Tape.Aux[Float, Float]] = constraint.value(w)

    def myNetwork(input: RAIITask[Tape.Aux[Float, Float]]): RAIITask[Tape.Aux[Float, Float]] = {
      //(??? : RAIITask[Tape.Aux[Float, Any]]) + input

      input + weight //.toRAIITask
    }

    def train(inputData: Float): Task[Unit] = {
      val c: RAIITask[Unit] = myNetwork(inputData).flatMap { outputTape =>
        RAIITask.unmanaged(outputTape.backward(Future.now(1.0f)))
      }
      new Task(c.run.run)
    }

    val t5: Task[Unit] = throwableMonadic[Task] {
      train(1.0f).each
      train(1.0f).each
      train(1.0f).each
      train(1.0f).each
      train(1.0f).each
    }

    val p = Promise[Assertion]
    t5.unsafePerformAsync { either: \/[Throwable, Unit] =>
      p.success {
        inside(either) {
          case -\/(e) => throw e
          case \/-(_) => {
            weight.data should be(-4)
          }
        }
      }
    }
    p.future
  }

}