/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.proton.plug.context.client;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.message.ProtonJMessage;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.proton.plug.AMQPClientReceiver;
import org.proton.plug.context.AbstractConnection;
import org.proton.plug.exceptions.HornetQAMQPException;
import org.proton.plug.context.AbstractProtonReceiver;
import org.proton.plug.context.SessionExtension;
import org.proton.plug.context.ProtonSessionCallback;

import static org.proton.plug.util.DeliveryUtil.readDelivery;
import static org.proton.plug.util.DeliveryUtil.decodeMessageImpl;

/**
 * @author Clebert Suconic
 */
public class ProtonClientReceiver extends AbstractProtonReceiver implements AMQPClientReceiver
{
   public ProtonClientReceiver(ProtonSessionCallback sessionSPI, AbstractConnection connection, SessionExtension protonSession, Receiver receiver)
   {
      super(sessionSPI, connection, protonSession, receiver);
   }

   public void onFlow(int credits)
   {
   }

   LinkedBlockingDeque<MessageImpl> queues = new LinkedBlockingDeque<>();
   /*
   * called when Proton receives a message to be delivered via a Delivery.
   *
   * This may be called more than once per deliver so we have to cache the buffer until we have received it all.
   *
   * */
   public void onMessage(Delivery delivery) throws HornetQAMQPException
   {
      ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(1024);
      try
      {
         synchronized (connection.getLock())
         {
            readDelivery(receiver, buffer);
            MessageImpl clientMessage = decodeMessageImpl(buffer);

            // This second method could be better
//            clientMessage.decode(buffer.nioBuffer());

            receiver.advance();
            delivery.disposition(Accepted.getInstance());
            queues.add(clientMessage);

         }
      }
      finally
      {
         buffer.release();
      }
   }


   @Override
   public ProtonJMessage receiveMessage(int time, TimeUnit unit) throws Exception
   {
      return queues.poll(time, unit);
   }
}