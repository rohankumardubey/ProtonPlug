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

package org.proton.plug.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.engine.Transport;
import org.proton.plug.AMQPConnectionCallback;
import org.proton.plug.AMQPConnectionContext;
import org.proton.plug.exceptions.HornetQAMQPException;
import org.proton.plug.handler.ProtonHandler;
import org.proton.plug.handler.SASLResult;
import org.proton.plug.handler.impl.DefaultEventHandler;

/**
 * Clebert Suconic
 */
public abstract class AbstractConnectionContext extends ProtonInitializable implements AMQPConnectionContext
{

   protected ProtonHandler handler = ProtonHandler.Factory.create();

   protected AMQPConnectionCallback connectionCallback;

   private final Map<Session, AbstractProtonSessionContext> sessions = new ConcurrentHashMap<>();


   public AbstractConnectionContext(AMQPConnectionCallback connectionCallback)
   {
      this.connectionCallback = connectionCallback;
      connectionCallback.setConnection(this);
      handler.addEventHandler(new LocalListener());
   }

   public SASLResult getSASLResult()
   {
      return handler.getSASLResult();
   }

   @Override
   public void inputBuffer(ByteBuf buffer)
   {
      handler.inputBuffer(buffer);
   }

   public void destroy()
   {
      connectionCallback.close();
   }


   public Object getLock()
   {
      return handler.getLock();
   }

   @Override
   public int capacity()
   {
      return handler.capacity();
   }

   @Override
   public void outputDone(int bytes)
   {
      handler.outputDone(bytes);
   }

   public void flush()
   {
      handler.flush();
   }

   public void close()
   {
      handler.close();
   }

   protected AbstractProtonSessionContext getSessionExtension(Session realSession) throws HornetQAMQPException
   {
      AbstractProtonSessionContext sessionExtension = sessions.get(realSession);
      if (sessionExtension == null)
      {
         // how this is possible? Log a warn here
         sessionExtension = newSessionExtension(realSession);
         realSession.setContext(sessionExtension);
         sessions.put(realSession, sessionExtension);
      }
      return sessionExtension;
   }

   protected abstract void remoteLinkOpened(Link link) throws Exception;


   protected abstract AbstractProtonSessionContext newSessionExtension(Session realSession) throws HornetQAMQPException;

   @Override
   public boolean checkDataReceived()
   {
      return handler.checkDataReceived();
   }

   @Override
   public long getCreationTime()
   {
      return handler.getCreationTime();
   }

   protected void flushBytes()
   {
      ByteBuf bytes;
      // handler.outputBuffer has the lock
      while ((bytes = handler.outputBuffer()) != null)
      {
         connectionCallback.onTransport(bytes, AbstractConnectionContext.this);
      }
   }


   // This listener will perform a bunch of things here
   class LocalListener extends DefaultEventHandler
   {
      @Override
      public void onTransport(Transport transport)
      {
         flushBytes();
      }

      @Override
      public void onRemoteOpen(Connection connection) throws Exception
      {
         synchronized (getLock())
         {
            connection.setContext(AbstractConnectionContext.this);
            connection.open();
         }
         initialise();
      }


      @Override
      public void onClose(Connection connection)
      {
         synchronized (getLock())
         {
            for (AbstractProtonSessionContext protonSession : sessions.values())
            {
               protonSession.close();
            }
            sessions.clear();
         }
         // We must force write the channel before we actually destroy the connection
         onTransport(handler.getTransport());
         destroy();
      }

      @Override
      public void onOpen(Session session) throws Exception
      {
         getSessionExtension(session);
      }

      @Override
      public void onRemoteOpen(Session session) throws Exception
      {
         getSessionExtension(session).initialise();
         synchronized (getLock())
         {
            session.open();
         }
      }


      @Override
      public void onClose(Session session) throws Exception
      {
         synchronized (getLock())
         {
            session.close();
         }

         AbstractProtonSessionContext sessionContext = (AbstractProtonSessionContext) session.getContext();
         if (sessionContext != null)
         {
            sessionContext.close();
            sessions.remove(session);
            session.setContext(null);
         }
      }

      @Override
      public void onRemoteClose(Session session) throws Exception
      {
         onClose(session);
      }

      @Override
      public void onRemoteOpen(Link link) throws Exception
      {
         remoteLinkOpened(link);
      }

      @Override
      public void onFlow(Link link) throws Exception
      {
         ((ProtonDeliveryHandler) link.getContext()).onFlow(link.getCredit());
      }

      @Override
      public void onRemoteClose(Link link) throws Exception
      {
         ((ProtonDeliveryHandler) link.getContext()).close();
      }

      public void onDelivery(Delivery delivery) throws Exception
      {
         ProtonDeliveryHandler handler = (ProtonDeliveryHandler) delivery.getLink().getContext();
         if (handler != null)
         {
            handler.onMessage(delivery);
         }
         else
         {
            // TODO: logs

            System.err.println("Handler is null, can't delivery " + delivery);
         }
      }


   }

}
