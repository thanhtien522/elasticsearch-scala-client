package rever.client4s

import com.twitter.util.{Future, Promise}
import org.elasticsearch.action.support.AbstractListenableActionFuture
import org.elasticsearch.action.{ActionRequest, ActionRequestBuilder, ActionResponse}
import org.elasticsearch.client.{Client, ElasticsearchClient}
import org.elasticsearch.threadpool.ThreadPool

import scala.annotation.tailrec


/**
 * Created by zkidkid on 10/6/16.
 */
object Elasticsearch {


  type Req[T <: ActionRequest[T]] = ActionRequest[T]
  type Resp = ActionResponse
  type Client[C <: ElasticsearchClient[C]] = ElasticsearchClient[C]

  implicit class ZActionRequestBuilder[I <: Req[I], J <: Resp, C <:Client[C], K <: ActionRequestBuilder[I, J, K, C]](arb: ActionRequestBuilder[I, J, K, C]) {

    private[this] val promise = Promise[J]()

    val internalThreadPool: ThreadPool = internalThreadPool(arb,arb.getClass)

    @tailrec
    private[this] def internalThreadPool(arb: ActionRequestBuilder[I,J,K,C],cls: Class[_]): ThreadPool = {
      if (cls.getSimpleName.equals("ActionRequestBuilder")) {
        val f = cls.getDeclaredField("threadPool")
        f.setAccessible(true)
        f.get(arb).asInstanceOf[ThreadPool]
      }
      else
        internalThreadPool(arb,cls.getSuperclass)
    }

    def asyncGet(): Future[J] = {
      val listener = new AbstractListenableActionFuture[J, J](true, internalThreadPool) {
        override def onFailure(e: Throwable): Unit = promise.raise(e)

        override def onResponse(result: J): Unit = promise.setValue(result)

        override def convert(listenerResponse: J): J = listenerResponse
      }
      arb.execute(listener)
      promise
    }
  }

}
