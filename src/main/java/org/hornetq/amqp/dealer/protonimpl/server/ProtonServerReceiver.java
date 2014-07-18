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

package org.hornetq.amqp.dealer.protonimpl.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.hornetq.amqp.dealer.exceptions.HornetQAMQPException;
import org.hornetq.amqp.dealer.exceptions.HornetQAMQPInternalErrorException;
import org.hornetq.amqp.dealer.logger.HornetQAMQPProtocolMessageBundle;
import org.hornetq.amqp.dealer.protonimpl.AbstractProtonConnection;
import org.hornetq.amqp.dealer.protonimpl.AbstractProtonReceiver;
import org.hornetq.amqp.dealer.protonimpl.ProtonSession;
import org.hornetq.amqp.dealer.spi.ProtonSessionSPI;

import static org.hornetq.amqp.dealer.util.DeliveryUtil.readDelivery;

/**
 * @author Clebert Suconic
 */

public class ProtonServerReceiver extends AbstractProtonReceiver
{

   private final int numberOfCredits;

   public ProtonServerReceiver(ProtonSessionSPI sessionSPI, AbstractProtonConnection connection, ProtonSession protonSession, Receiver receiver)
   {
      super(sessionSPI, connection, protonSession, receiver);
      this.numberOfCredits = connection.getNumberOfCredits();
   }

   public void onFlow(int credits)
   {
   }


   @Override
   public void initialise() throws HornetQAMQPException
   {
      super.initialise();
      org.apache.qpid.proton.amqp.messaging.Target target = (org.apache.qpid.proton.amqp.messaging.Target) receiver.getRemoteTarget();

      if (target != null)
      {
         if (target.getDynamic())
         {
            //if dynamic we have to create the node (queue) and set the address on the target, the node is temporary and
            // will be deleted on closing of the session
            String queue = sessionSPI.tempQueueName();


            try
            {
               sessionSPI.createTemporaryQueue(queue);
            }
            catch (Exception e)
            {
               throw new HornetQAMQPInternalErrorException(e.getMessage(), e);
            }
            target.setAddress(queue.toString());
         }
         else
         {
            //if not dynamic then we use the targets address as the address to forward the messages to, however there has to
            //be a queue bound to it so we nee to check this.
            String address = target.getAddress();
            if (address == null)
            {
               throw HornetQAMQPProtocolMessageBundle.BUNDLE.targetAddressNotSet();
            }
            try
            {
               if (!sessionSPI.queueQuery(address))
               {
                  throw HornetQAMQPProtocolMessageBundle.BUNDLE.addressDoesntExist();
               }
            }
            catch (Exception e)
            {
               throw HornetQAMQPProtocolMessageBundle.BUNDLE.errorFindingTemporaryQueue(e.getMessage());
            }
         }
      }

      flow(numberOfCredits);
   }

   /*
   * called when Proton receives a message to be delivered via a Delivery.
   *
   * This may be called more than once per deliver so we have to cache the buffer until we have received it all.
   *
   * */
   public void onMessage(Delivery delivery) throws HornetQAMQPException
   {
      Receiver receiver;
      try
      {
         receiver = ((Receiver) delivery.getLink());

         if (!delivery.isReadable())
         {
            return;
         }

         ByteBuf buffer = PooledByteBufAllocator.DEFAULT.heapBuffer(10 * 1024);
         try
         {
            synchronized (connection.getTrio().getLock())
            {
               readDelivery(receiver, buffer);

               sessionSPI.serverSend(receiver, delivery, address, delivery.getMessageFormat(), buffer);

               receiver.advance();
               delivery.disposition(Accepted.getInstance());
               delivery.settle();

               if (receiver.getRemoteCredit() < numberOfCredits / 2)
               {
                  flow(numberOfCredits);
               }
            }
         }
         finally
         {
            buffer.release();
         }
      }
      catch (Exception e)
      {
         e.printStackTrace();
         Rejected rejected = new Rejected();
         ErrorCondition condition = new ErrorCondition();
         condition.setCondition(Symbol.valueOf("failed"));
         condition.setDescription(e.getMessage());
         rejected.setError(condition);
         delivery.disposition(rejected);
      }
   }

}
